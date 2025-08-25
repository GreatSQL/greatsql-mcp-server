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
}
