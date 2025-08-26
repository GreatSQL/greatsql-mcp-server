package org.greatsql.greatsqlmcp.service;

import org.greatsql.greatsqlmcp.entity.DatabaseInfo;
import org.greatsql.greatsqlmcp.entity.TableInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {
    @Autowired
    private ConnectionService connectionService;

    @Tool(name = "listDatabases", description = "列出服务器上所有可用的数据库")
    public List<DatabaseInfo> listDatabases() {
        List<DatabaseInfo> databases = new ArrayList<>();
        String sql = "SHOW DATABASES";

        try (Connection conn = connectionService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                DatabaseInfo dbInfo = new DatabaseInfo(
                        rs.getString("Database")
                );
                databases.add(dbInfo);
            }
        } catch (SQLException e) {
            throw new RuntimeException("无法获取数据库列表：" + e.getMessage(), e);
        }
        return databases;
    }


    @Tool(name = "listTables", description = "列出指定数据库中的所有表")
    public List<TableInfo> listTables(
            @ToolParam(description = "数据库名称") String database) {
        System.out.println("listTables called with database: " + database);

        List<TableInfo> tables = new ArrayList<>();
        String sql = "SELECT table_name,table_schema,table_rows,create_time,table_comment FROM information_schema.tables WHERE table_schema=?";

        try (Connection conn = connectionService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, database);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TableInfo tableInfo = new TableInfo(
                            rs.getString("table_name"),
                            rs.getString("table_schema"),
                            rs.getLong("table_rows"),
                            rs.getString("create_time")
                    );
                    tables.add(tableInfo);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("无法获取数据库列表：" + e.getMessage(), e);
        }
        return tables;
    }

    @Tool(name = "getTableRowCount", description = "获取指定表的数据行数")
    public long getTableRowCount(
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "表名") String tableName) {

        long count = 0;
        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (Connection conn = connectionService.getConnection(database);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("无法获取表行数：" + e.getMessage(), e);
        }

        return count;
    }

    @Tool(name = "executeQuery", description = "在指定数据库中执行SQL查询")
    public List<Map<String, Object>> executeQuery(
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "SQL查询语句") String query) {

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = connectionService.getConnection(database);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            int columnCount = rs.getMetaData().getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("执行查询时出错：" + e.getMessage(), e);
        }

        return results;
    }

    @Tool(name = "insertData", description = "向指定表插入数据")
    public int insertData(
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "表名") String tableName,
            @ToolParam(description = "字段名列表，用逗号分隔") String columns,
            @ToolParam(description = "对应字段的值列表，用逗号分隔") String values) {

        String[] columnArray = columns.split(",");
        String[] valueArray = values.split(",");

        if (columnArray.length != valueArray.length) {
            throw new RuntimeException("字段数量与值数量不匹配");
        }

        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " (");
        for (int i = 0; i < columnArray.length; i++) {
            sql.append(columnArray[i].trim());
            if (i < columnArray.length - 1) {
                sql.append(", ");
            }
        }
        sql.append(") VALUES (");
        for (int i = 0; i < valueArray.length; i++) {
            sql.append("?");
            if (i < valueArray.length - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");

        try (Connection conn = connectionService.getConnection(database);
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < valueArray.length; i++) {
                String rawValue = valueArray[i].trim();
                if ((rawValue.startsWith("'") && rawValue.endsWith("'")) ||
                        (rawValue.startsWith("\"") && rawValue.endsWith("\""))) {
                    rawValue = rawValue.substring(1, rawValue.length() - 1);
                }
                stmt.setString(i + 1, rawValue);
            }

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected;

        } catch (SQLException e) {
            throw new RuntimeException("插入数据时出错：" + e.getMessage(), e);
        }
    }

    @Tool(name = "updateData", description = "更新指定表的数据")
    public int updateData(
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "表名") String tableName,
            @ToolParam(description = "更新的字段和值，格式: field1=value1,field2=value2") String setClause,
            @ToolParam(description = "WHERE条件，格式: field=value") String whereClause) {

        String[] setPairs = setClause.split(",");
        List<String> setValues = new ArrayList<>();

        StringBuilder sql = new StringBuilder("UPDATE " + tableName + " SET ");
        for (int i = 0; i < setPairs.length; i++) {
            String[] pair = setPairs[i].split("=");
            if (pair.length != 2) {
                throw new RuntimeException("SET子句格式错误: " + setPairs[i]);
            }
            sql.append(pair[0].trim()).append(" = ?");
            String rawValue = pair[1].trim();
            if (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length() >= 2) {
                rawValue = rawValue.substring(1, rawValue.length() - 1);
            }
            setValues.add(rawValue);
            if (i < setPairs.length - 1) {
                sql.append(", ");
            }
        }

        if (whereClause != null && !whereClause.trim().isEmpty()) {
            String[] wherePair = whereClause.split("=");
            if (wherePair.length != 2) {
                throw new RuntimeException("WHERE子句格式错误: " + whereClause);
            }
            sql.append(" WHERE ").append(wherePair[0].trim()).append(" = ?");
            String rawWhereValue = wherePair[1].trim();
            if (rawWhereValue.startsWith("'") && rawWhereValue.endsWith("'") && rawWhereValue.length() >= 2) {
                rawWhereValue = rawWhereValue.substring(1, rawWhereValue.length() - 1);
            }

            setValues.add(rawWhereValue);
        }

        try (Connection conn = connectionService.getConnection(database);
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < setValues.size(); i++) {
                stmt.setString(i + 1, setValues.get(i));
            }

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected;

        } catch (SQLException e) {
            throw new RuntimeException("更新数据时出错：" + e.getMessage(), e);
        }
    }

    @Tool(name = "deleteData", description = "删除指定表的数据")
    public int deleteData(
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "表名") String tableName,
            @ToolParam(description = "WHERE条件，格式: field=value 或 field!=value 等") String whereClause) {

        if (whereClause == null || whereClause.trim().isEmpty()) {
            throw new RuntimeException("删除操作必须提供WHERE条件以确保安全");
        }

        String[] operators = {"!=", ">=", "<=", "=", ">", "<"};
        String operator = null;
        String field = null;
        String value = null;
        for (String op : operators) {
            int idx = whereClause.indexOf(op);
            if (idx > 0) {
                operator = op;
                field = whereClause.substring(0, idx).trim();
                value = whereClause.substring(idx + op.length()).trim();
                break;
            }
        }
        if (operator == null || field == null || value == null) {
            throw new RuntimeException("WHERE子句格式错误: " + whereClause);
        }
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }

        String sql = "DELETE FROM " + tableName + " WHERE " + field + " " + operator + " ?";

        try (Connection conn = connectionService.getConnection(database);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, value);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected;

        } catch (SQLException e) {
            throw new RuntimeException("删除数据时出错：" + e.getMessage(), e);
        }
    }

    @Tool(name = "createDB", description = "创建新数据库")
    public boolean createDB(
            @ToolParam(description = "数据库名称") String databaseName) {
        String sql = "CREATE DATABASE " + databaseName;

        try (Connection conn = connectionService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("创建数据库时出错：" + e.getMessage(), e);
        }
    }

    @Tool(name = "checkCriticalTransactions", description = "检查当前是否有活跃的大事务或长事务")
    public List<Map<String, Object>> checkCriticalTransactions() {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM information_schema.INNODB_TRX WHERE " +
                "trx_lock_structs >= 5 OR " +
                "trx_rows_locked >= 100 OR " +
                "trx_rows_modified >= 100 OR " +
                "TIME_TO_SEC(TIMEDIFF(NOW(),trx_started)) > 100";

        try (Connection conn = connectionService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int columnCount = rs.getMetaData().getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询需要关注的事务时出错：" + e.getMessage(), e);
        }

        return results;
    }

    @Tool(name = "avgSQLRT", description = "计算SQL请求平均响应耗时")
    public double avgSQLRT() {
        String sql = "SELECT BENCHMARK(1000000,AES_ENCRYPT('hello','GreatSQL'))";
        long totalTime = 0;
        int iterations = 10;
        
        try (Connection conn = connectionService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (int i = 0; i < iterations; i++) {
                long startTime = System.currentTimeMillis();
                stmt.executeQuery();
                long endTime = System.currentTimeMillis();
                totalTime += (endTime - startTime);
                Thread.sleep(1000); // 间隔1秒
            }
            
            double avgTime = (double) totalTime / iterations;
            
            if (avgTime > 50) {
                System.out.println("严重级告警：SQL请求平均响应耗时 " + avgTime + " ms");
            } else if (avgTime > 10) {
                System.out.println("一般级告警：SQL请求平均响应耗时 " + avgTime + " ms");
            }
            
            return avgTime;
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("计算SQL请求平均响应耗时失败：" + e.getMessage(), e);
        }
    }

    @Tool(name = "listNotableWaitEvents", description = "检查需要关注的数据库等待事件")
    public Map<String, String> listNotableWaitEvents() {
        Map<String, String> results = new HashMap<>();
        
        try (Connection conn = connectionService.getConnection()) {
            // 1. 检查行锁等待
            checkRowLockWaits(conn, results);
            
            // 2. 检查Buffer Pool等待
            checkBufferPoolWaits(conn, results);
            
            // 3. 检查Redo Log等待
            checkRedoLogWaits(conn, results);
            
            // 4. 检查Undo Log清理
            checkUndoLogPurge(conn, results);
            
        } catch (SQLException e) {
            throw new RuntimeException("检查等待事件时出错：" + e.getMessage(), e);
        }
        
        return results;
    }
    
    private void checkRowLockWaits(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT variable_value FROM performance_schema.global_status " +
                     "WHERE variable_name = 'Innodb_row_lock_current_waits'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > 10) {
                    results.put("row_lock_wait", "严重级告警：当前有 " + value + " 个活跃的行锁等待，请DBA立即介入检查");
                } else if (value > 0) {
                    results.put("row_lock_wait", "一般级告警：当前有 " + value + " 个活跃的行锁等待，建议DBA检查");
                }
            }
        }
    }
    
    private void checkBufferPoolWaits(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT variable_value FROM performance_schema.global_status " +
                     "WHERE variable_name = 'Innodb_buffer_pool_wait_free'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > 10) {
                    results.put("buffer_pool_wait", "严重级告警：Buffer Pool等待事件 " + value + " 次，请立即调大innodb_buffer_pool_size并检查");
                } else if (value > 0) {
                    results.put("buffer_pool_wait", "一般级告警：Buffer Pool等待事件 " + value + " 次，建议调大innodb_buffer_pool_size");
                }
            }
        }
    }
    
    private void checkRedoLogWaits(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT variable_value FROM performance_schema.global_status " +
                     "WHERE variable_name = 'Innodb_log_waits'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > 10) {
                    results.put("redo_log_wait", "严重级告警：Redo Log等待事件 " + value + " 次，请立即调大innodb_log_buffer_size并检查");
                } else if (value > 0) {
                    results.put("redo_log_wait", "一般级告警：Redo Log等待事件 " + value + " 次，建议调大innodb_log_buffer_size");
                }
            }
        }
    }
    
    private void checkUndoLogPurge(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT COUNT, COMMENT FROM information_schema.INNODB_METRICS " +
                     "WHERE NAME = 'trx_rseg_history_len'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int value = rs.getInt(1);
                if (value > 5000) {
                    results.put("undo_log_purge", "严重级告警：未清理的undo log数量 " + value + "，请DBA立即介入检查");
                } else if (value > 1000) {
                    results.put("undo_log_purge", "一般级告警：未清理的undo log数量 " + value + "，建议DBA检查");
                }
            }
        }
    }

    @Tool(name = "checkMGRStatus", description = "监控MGR集群状态")
    public Map<String, String> checkMGRStatus() {
        Map<String, String> results = new HashMap<>();
        
        try (Connection conn = connectionService.getConnection()) {
            // 1. 检查MGR是否已启用
            checkMGREnabled(conn, results);
            
            // 2. 检查MGR事务队列状态
            checkMGRTransactionQueue(conn, results);
            
        } catch (SQLException e) {
            throw new RuntimeException("检查MGR状态时出错：" + e.getMessage(), e);
        }
        
        return results;
    }
    
    private void checkMGREnabled(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT * FROM performance_schema.replication_group_members";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                results.put("mgr_enabled", "当前没有启用MGR");
                return;
            }
            
            boolean hasOnlineMember = false;
            do {
                if ("ONLINE".equals(rs.getString("MEMBER_STATE"))) {
                    hasOnlineMember = true;
                    break;
                }
            } while (rs.next());
            
            if (!hasOnlineMember) {
                results.put("mgr_status", "严重级告警：MGR已启用但无ONLINE状态的成员");
            } else {
                results.put("mgr_status", "MGR运行正常");
            }
        }
    }
    
    private void checkMGRTransactionQueue(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT MEMBER_ID as id, COUNT_TRANSACTIONS_IN_QUEUE as trx_tobe_certified, " +
                     "COUNT_TRANSACTIONS_REMOTE_IN_APPLIER_QUEUE as relaylog_tobe_applied " +
                     "FROM performance_schema.replication_group_member_stats";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int trxToCertify = rs.getInt("trx_tobe_certified");
                int relaylogToApply = rs.getInt("relaylog_tobe_applied");
                
                if (trxToCertify > 100) {
                    results.put("mgr_trx_certify", "严重级告警：待认证事务队列大小 " + trxToCertify);
                } else if (trxToCertify > 10) {
                    results.put("mgr_trx_certify", "一般级关注：待认证事务队列大小 " + trxToCertify);
                }
                
                if (relaylogToApply > 100) {
                    results.put("mgr_relaylog_apply", "严重级告警：待回放事务队列大小 " + relaylogToApply);
                } else if (relaylogToApply > 10) {
                    results.put("mgr_relaylog_apply", "一般级关注：待回放事务队列大小 " + relaylogToApply);
                }
            }
        }
    }

    @Tool(name = "findAbnormalMemoryIssue", description = "检查数据库中是否存在内存异常情况")
    public Map<String, String> findAbnormalMemoryIssue() {
        Map<String, String> results = new HashMap<>();
        
        try (Connection conn = connectionService.getConnection()) {
            // 1. 检查全局内存模块异常
            checkGlobalMemoryEvents(conn, results);
            
            // 2. 检查线程内存异常
            checkThreadMemoryEvents(conn, results);
            
        } catch (SQLException e) {
            throw new RuntimeException("检查内存异常时出错：" + e.getMessage(), e);
        }
        
        return results;
    }
    
    private void checkGlobalMemoryEvents(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT EVENT_NAME, SUM_NUMBER_OF_BYTES_ALLOC FROM " +
                     "PERFORMANCE_SCHEMA.MEMORY_SUMMARY_GLOBAL_BY_EVENT_NAME " +
                     "WHERE SUM_NUMBER_OF_BYTES_ALLOC >= 1073741824 " +
                     "ORDER BY SUM_NUMBER_OF_BYTES_ALLOC DESC";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String eventName = rs.getString("EVENT_NAME");
                long bytesAlloc = rs.getLong("SUM_NUMBER_OF_BYTES_ALLOC");
                
                if ("memory/innodb/buf_buf_pool".equals(eventName)) {
                    // 检查innodb buffer pool是否异常溢出
                    long bufferPoolSize = getInnoDBBufferPoolSize(conn);
                    if (bytesAlloc > bufferPoolSize * 1.2) {
                        results.put("memory_innodb_buffer_pool", "严重级告警：InnoDB Buffer Pool内存使用量（" + bytesAlloc + " bytes）超过配置值（" + bufferPoolSize + " bytes），可能存在内存泄漏风险");
                    }
                } else if (eventName.startsWith("memory/sql/") || "memory/memory/HP_PTRS".equals(eventName) || "memory/sql/Filesort_buffer::sort_keys".equals(eventName)) {
                    results.put("memory_inefficient_sql", "一般级告警：模块 " + eventName + " 内存使用量较高（" + bytesAlloc + " bytes），可能存在低效SQL，建议检查慢查询并优化");
                }
            }
        }
    }
    
    private long getInnoDBBufferPoolSize(Connection conn) throws SQLException {
        String sql = "SHOW VARIABLES LIKE 'innodb_buffer_pool_size'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("Value");
            }
        }
        return 0;
    }
    
    private void checkThreadMemoryEvents(Connection conn, Map<String, String> results) throws SQLException {
        String sql = "SELECT THREAD_ID, EVENT_NAME, SUM_NUMBER_OF_BYTES_ALLOC FROM " +
                     "PERFORMANCE_SCHEMA.MEMORY_SUMMARY_BY_THREAD_BY_EVENT_NAME " +
                     "WHERE SUM_NUMBER_OF_BYTES_ALLOC >= 1073741824 " +
                     "ORDER BY SUM_NUMBER_OF_BYTES_ALLOC DESC";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            int highMemoryThreads = 0;
            while (rs.next()) {
                String eventName = rs.getString("EVENT_NAME");
                long bytesAlloc = rs.getLong("SUM_NUMBER_OF_BYTES_ALLOC");
                
                if (eventName.startsWith("memory/innodb/") || eventName.startsWith("memory/sql/")) {
                    highMemoryThreads++;
                }
            }
            
            if (highMemoryThreads > 10) {
                results.put("memory_high_threads", "严重级告警：当前有 " + highMemoryThreads + " 个线程内存使用量超过1GB，可能存在大量活跃连接或低效SQL，建议检查慢查询并优化");
            } else if (highMemoryThreads > 0) {
                results.put("memory_high_threads", "一般级告警：当前有 " + highMemoryThreads + " 个线程内存使用量超过1GB，建议检查慢查询并优化");
            }
        }
    }
    
    public Map<String, String> findImproperVars() {
        Map<String, String> results = new HashMap<>();
        
        try (Connection conn = connectionService.getConnection()) {
            // 查询全局变量和状态
            Map<String, String> vars = getGlobalVariables(conn);
            Map<String, String> stats = getGlobalStatus(conn);
            
            // 检查连接数配置
            checkMaxConnections(vars, stats, results);
            
            // 检查表缓存配置
            checkTableCaches(vars, stats, results);
            
            // 检查线程缓存配置
            checkThreadCache(vars, stats, results);
            
            // 检查临时表配置
            checkTempTables(vars, stats, results);
            
            // 检查 InnoDB 日志配置
            checkInnoDBLogs(vars, results);
            
            // 检查二进制日志和事务提交配置
            checkBinaryLogAndFlush(vars, results);
            
            // 检查InnoDB日志配置
            checkInnoDBLogs(vars, results);
            
            // 检查并行复制配置
            checkParallelReplication(vars, results);
            
            // 检查IO容量配置
            checkIOCapacity(vars, results);
            
            // 检查并发线程配置
            checkThreadConcurrency(vars, results);
            
            // 检查二进制日志格式
            checkBinlogFormat(vars, results);
            
            // 检查日志缓冲区配置
            checkLogBuffer(vars, results);
            
            // 检查其他推荐配置
            checkRecommendedSettings(vars, results);
            
            // 检查慢查询日志配置
            checkSlowQuerySettings(vars, results);
            
            // 检查缓冲池配置
            checkBufferPoolSize(vars, results);
            
        } catch (SQLException e) {
            throw new RuntimeException("检查配置参数时出错：" + e.getMessage(), e);
        }
        
        return results;
    }
    
    private Map<String, String> getGlobalVariables(Connection conn) throws SQLException {
        Map<String, String> vars = new HashMap<>();
        String sql = "SELECT * FROM performance_schema.global_variables";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                vars.put(rs.getString("VARIABLE_NAME"), rs.getString("VARIABLE_VALUE"));
            }
        }
        return vars;
    }
    
    private Map<String, String> getGlobalStatus(Connection conn) throws SQLException {
        Map<String, String> stats = new HashMap<>();
        String sql = "SELECT * FROM performance_schema.global_status";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                stats.put(rs.getString("VARIABLE_NAME"), rs.getString("VARIABLE_VALUE"));
            }
        }
        return stats;
    }
    
    private void checkMaxConnections(Map<String, String> vars, Map<String, String> stats, Map<String, String> results) {
        if (vars.containsKey("max_connections") && stats.containsKey("Threads_connected")) {
            int maxConn = Integer.parseInt(vars.get("max_connections"));
            int currConn = Integer.parseInt(stats.get("Threads_connected"));
            
            if (currConn >= maxConn * 0.8) {
                results.put("max_connections", "警告：当前连接数(" + currConn + ")已接近最大连接数限制(" + maxConn + ")，建议考虑增加max_connections参数值");
            }
        }
    }
    
    private void checkTableCaches(Map<String, String> vars, Map<String, String> stats, Map<String, String> results) {
        // 检查table_open_cache
        if (vars.containsKey("table_open_cache") && stats.containsKey("Open_tables") && stats.containsKey("Opened_tables")) {
            int cacheSize = Integer.parseInt(vars.get("table_open_cache"));
            int openTables = Integer.parseInt(stats.get("Open_tables"));
            int openedTables = Integer.parseInt(stats.get("Opened_tables"));
            
            if (openTables >= cacheSize * 0.8 && openedTables > openTables * 10) {
                results.put("table_open_cache", "警告：当前打开表数(" + openTables + ")接近缓存限制(" + cacheSize + ")且表打开次数(" + openedTables + ")较高，建议增加table_open_cache参数值");
            }
        }
        
        // 检查table_definition_cache
        if (vars.containsKey("table_definition_cache") && stats.containsKey("Open_table_definitions") && stats.containsKey("Opened_table_definitions")) {
            int cacheSize = Integer.parseInt(vars.get("table_definition_cache"));
            int openDefs = Integer.parseInt(stats.get("Open_table_definitions"));
            int openedDefs = Integer.parseInt(stats.get("Opened_table_definitions"));
            
            if (openDefs >= cacheSize * 0.8 && openedDefs > openDefs * 10) {
                results.put("table_definition_cache", "警告：当前打开表定义数(" + openDefs + ")接近缓存限制(" + cacheSize + ")且表定义打开次数(" + openedDefs + ")较高，建议增加table_definition_cache参数值");
            }
        }
    }
    
    private void checkThreadCache(Map<String, String> vars, Map<String, String> stats, Map<String, String> results) {
        if (vars.containsKey("thread_cache_size") && stats.containsKey("Threads_cached") && stats.containsKey("Threads_created")) {
            int cacheSize = Integer.parseInt(vars.get("thread_cache_size"));
            int cachedThreads = Integer.parseInt(stats.get("Threads_cached"));
            int createdThreads = Integer.parseInt(stats.get("Threads_created"));
            
            if (cachedThreads <= cacheSize * 0.2 && createdThreads > cachedThreads * 10) {
                results.put("thread_cache_size", "警告：线程缓存使用率低(" + cachedThreads + "/" + cacheSize + ")但线程创建次数高(" + createdThreads + ")，建议增加thread_cache_size参数值");
            }
        }
    }
    
    private void checkTempTables(Map<String, String> vars, Map<String, String> stats, Map<String, String> results) {
        if (stats.containsKey("Created_tmp_disk_tables") && stats.containsKey("Created_tmp_tables")) {
            int diskTmpTables = Integer.parseInt(stats.get("Created_tmp_disk_tables"));
            int memTmpTables = Integer.parseInt(stats.get("Created_tmp_tables"));
            
            if (diskTmpTables > 100 || diskTmpTables > memTmpTables * 0.1) {
                results.put("tmp_table_size", "警告：磁盘临时表创建次数高(" + diskTmpTables + ")，建议增加tmp_table_size和max_heap_table_size参数值(至少96MB)");
            }
        }
    }
    
    private void checkBinaryLogAndFlush(Map<String, String> vars, Map<String, String> results) {
        if (vars.containsKey("sync_binlog") && vars.get("sync_binlog").equals("0")) {
            results.put("sync_binlog", "警告：sync_binlog参数设置为0，服务器掉电时可能丢失二进制日志数据，建议设置为1");
        }
        
        if (vars.containsKey("innodb_flush_log_at_trx_commit") && vars.get("innodb_flush_log_at_trx_commit").equals("0")) {
            results.put("innodb_flush_log_at_trx_commit", "警告：innodb_flush_log_at_trx_commit参数设置为0，服务器掉电时可能丢失事务数据，建议设置为1");
        }
    }
    
    private void checkInnoDBLogs(Map<String, String> vars, Map<String, String> results) {
        // 检查redo日志大小
        long var1 = 0;
        long var2 = 0;
        if (vars.containsKey("innodb_log_file_size") && vars.containsKey("innodb_log_files_in_group")) {
            var1 = Long.parseLong(vars.get("innodb_log_file_size")) * Long.parseLong(vars.get("innodb_log_files_in_group"));
        }
        if (vars.containsKey("innodb_redo_log_capacity")) {
            var2 = Long.parseLong(vars.get("innodb_redo_log_capacity"));
        }
        
        if (var1 < 2147483648L && var2 < 2147483648L) { // 2GB
            results.put("innodb_redo_space", "InnoDB Redo 空间可能不够用，会影响性能");
        }
        
        // 检查日志缓冲区大小
        if (vars.containsKey("innodb_log_buffer_size")) {
            long logBufferSize = Long.parseLong(vars.get("innodb_log_buffer_size"));
            if (logBufferSize < 33554432) { // 32MB
                results.put("innodb_log_buffer_size", "建议：innodb_log_buffer_size参数值(" + (logBufferSize / 1024 / 1024) + "MB)较小，建议设置为至少32MB");
            }
        }
    }
    
    private void checkParallelReplication(Map<String, String> vars, Map<String, String> results) {
        String parallelType = vars.getOrDefault("slave_parallel_type", vars.getOrDefault("replica_parallel_type", ""));
        if (!parallelType.equals("LOGICAL_CLOCK")) {
            results.put("parallel_replication", "警告：并行复制类型设置为" + parallelType + "，建议设置为LOGICAL_CLOCK以获得更好的并行复制性能");
        }
    }
    
    private void checkIOCapacity(Map<String, String> vars, Map<String, String> results) {
        if (vars.containsKey("innodb_io_capacity")) {
            int ioCapacity = Integer.parseInt(vars.get("innodb_io_capacity"));
            if (ioCapacity < 10000) {
                results.put("innodb_io_capacity", "警告：innodb_io_capacity参数值(" + ioCapacity + ")较低，建议设置为至少10000");
            }
        }
        
        if (vars.containsKey("innodb_io_capacity_max")) {
            int ioCapacityMax = Integer.parseInt(vars.get("innodb_io_capacity_max"));
            if (ioCapacityMax < 10000) {
                results.put("innodb_io_capacity_max", "警告：innodb_io_capacity_max参数值(" + ioCapacityMax + ")较低，建议设置为至少10000");
            }
        }
    }
    
    private void checkThreadConcurrency(Map<String, String> vars, Map<String, String> results) {
        if (vars.containsKey("innodb_thread_concurrency") && !vars.get("innodb_thread_concurrency").equals("0")) {
            results.put("innodb_thread_concurrency", "警告：innodb_thread_concurrency参数设置为" + vars.get("innodb_thread_concurrency") + "，建议设置为0以获得更好的并发性能");
        }
    }
    
    private void checkBinlogFormat(Map<String, String> vars, Map<String, String> results) {
        if (vars.containsKey("binlog_format") && !vars.get("binlog_format").equals("ROW")) {
            results.put("binlog_format", "警告：binlog_format参数设置为" + vars.get("binlog_format") + "，建议设置为ROW以确保数据安全");
        }
    }
    
    private void checkLogBuffer(Map<String, String> vars, Map<String, String> results) {
        // 已在checkInnoDBLogs中检查
    }
    
    private void checkRecommendedSettings(Map<String, String> vars, Map<String, String> results) {
        // 检查各种缓冲区大小
        checkBufferSize(vars, "sort_buffer_size", 4194304, results); // 4MB
        checkBufferSize(vars, "join_buffer_size", 4194304, results);
        checkBufferSize(vars, "read_rnd_buffer_size", 4194304, results);
        checkBufferSize(vars, "read_buffer_size", 4194304, results);
        
        // 检查其他推荐设置
        checkSetting(vars, "innodb_open_files", "65534", results);
        checkSetting(vars, "innodb_flush_method", "O_DIRECT", results);
        checkSetting(vars, "innodb_use_fdatasync", "ON", results);
        checkSetting(vars, "innodb_adaptive_hash_index", "OFF", results);
        checkSetting(vars, "innodb_doublewrite_pages", "128", results);
    }
    
    private void checkBufferSize(Map<String, String> vars, String param, long minSize, Map<String, String> results) {
        if (vars.containsKey(param)) {
            long size = Long.parseLong(vars.get(param));
            if (size < minSize) {
                results.put(param, "建议：" + param + "参数值(" + (size / 1024 / 1024) + "MB)较小，建议设置为至少" + (minSize / 1024 / 1024) + "MB");
            }
        }
    }
    
    private void checkSetting(Map<String, String> vars, String param, String recommended, Map<String, String> results) {
        if (vars.containsKey(param) && !vars.get(param).equals(recommended)) {
            results.put(param, "建议：" + param + "参数设置为" + vars.get(param) + "，推荐设置为" + recommended);
        }
    }
    
    private void checkSlowQuerySettings(Map<String, String> vars, Map<String, String> results) {
        if (vars.containsKey("long_query_time")) {
            double longQueryTime = Double.parseDouble(vars.get("long_query_time"));
            if (longQueryTime > 1.0) {
                results.put("long_query_time", "建议：long_query_time参数值(" + longQueryTime + ")较大，建议设置为0.05-1.0之间");
            }
        }
    }
    
    private void checkBufferPoolSize(Map<String, String> vars, Map<String, String> results) {
        if (vars.containsKey("innodb_buffer_pool_size")) {
            long bufferPoolSize = Long.parseLong(vars.get("innodb_buffer_pool_size"));
            if (bufferPoolSize < 2147483648L) { // 2GB
                results.put("innodb_buffer_pool_size", "警告：innodb_buffer_pool_size参数值(" + (bufferPoolSize / 1024 / 1024) + "MB)较小，建议设置为至少2GB");
            }
        }
    }
}
