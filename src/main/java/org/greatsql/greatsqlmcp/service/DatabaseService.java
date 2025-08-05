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

}