package org.greatsql.greatsqlmcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.greatsql.greatsqlmcp.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.greatsql.greatsqlmcp.config.AuthConfig;
import org.springframework.http.HttpStatus;
import java.util.Map;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;

@RestController
@CrossOrigin(origins = "*")
public class McpController {
    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Autowired
    private AuthConfig authConfig;

    @PostMapping("/mcp")
    public ResponseEntity<?> handleMcpPathRequest(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authConfig.isAuthEnabled()) {
            if (authHeader == null || !checkApiKey(authHeader)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "jsonrpc", "2.0",
                                "id", request.getOrDefault("id", "unknown"),
                                "error", Map.of(
                                        "code", -32001,
                                        "message", "认证失败：无效的API Key"
                                )
                        ));
            }
        }

        return handleMcpRequest(request);
    }

    private boolean checkApiKey(String authHeader) {
        if (authHeader == null|| !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String apiKey = authHeader.substring(7);
        return authConfig.getApiKey().equals(apiKey);
    }


    private ResponseEntity<?> handleMcpRequest(Map<String, Object> request) {
        try {
            String method = (String) request.get("method");
            if (method == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "method不能为空"));
            }

            Object id = request.get("id");
            Map<String, Object> params = (Map<String, Object>) request.get("params");

            Object result = switch (method) {
                case "initialize" -> handleInitialize(params);
                case "tools/list" -> handleToolsList();
                case "tools/call" -> handleToolsCall(params);
                default -> Map.of("error", "未知的方法: " + method);
            };

            if (result == null) {
                result = Map.of("error", "方法执行返回null");
            }

            Map<String, Object> response = Map.of(
                    "jsonrpc", "2.0",
                    "id", id != null ? id : "unknown",
                    "result", result
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("处理MCP请求时出错: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.ok(Map.of(
                    "jsonrpc", "2.0",
                    "id", request.getOrDefault("id", "unknown"),
                    "error", Map.of(
                            "code", -32603,
                            "message", "内部服务器错误: " + e.getMessage()
                    )
            ));
        }
    }

    private Object handleInitialize(Map<String, Object> params) {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "greatsql-mcp",
                        "version", "1.0.0"
                )
        );
    }

    private Object handleToolsList() {
        return Map.of(
                "tools", new Object[]{
                        Map.of(
                                "name", "listDatabases",
                                "description", "列出服务器上所有可用的数据库",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        ),
                        Map.of(
                                "name", "listTables",
                                "description", "列出指定数据库中的所有表",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "database", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                )
                                        ),
                                        "required", new String[]{"database"}
                                )
                        ),
                        Map.of(
                                "name", "executeQuery",
                                "description", "在指定数据库中执行SQL查询",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "database", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                ),
                                                "query", Map.of(
                                                        "type", "string",
                                                        "description", "SQL查询语句"
                                                )
                                        ),
                                        "required", new String[]{"database", "query"}
                                )
                        ),
                        Map.of(
                                "name", "getTableRowCount",
                                "description", "获取指定表的数据行数",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "database", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                ),
                                                "tableName", Map.of(
                                                        "type", "string",
                                                        "description", "表名"
                                                )
                                        ),
                                        "required", new String[]{"database", "tableName"}
                                )
                        ),
                        Map.of(
                                "name", "insertData",
                                "description", "向指定表插入数据",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "database", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                ),
                                                "tableName", Map.of(
                                                        "type", "string",
                                                        "description", "表名"
                                                ),
                                                "columns", Map.of(
                                                        "type", "string",
                                                        "description", "字段名列表，用逗号分隔"
                                                ),
                                                "values", Map.of(
                                                        "type", "string",
                                                        "description", "对应字段的值列表，用逗号分隔"
                                                )
                                        ),
                                        "required", new String[]{"database", "tableName", "columns", "values"}
                                )
                        ),
                        Map.of(
                                "name", "updateData",
                                "description", "更新指定表的数据",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "database", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                ),
                                                "tableName", Map.of(
                                                        "type", "string",
                                                        "description", "表名"
                                                ),
                                                "setClause", Map.of(
                                                        "type", "string",
                                                        "description", "更新的字段和值，格式: field1=value1,field2=value2"
                                                ),
                                                "whereClause", Map.of(
                                                        "type", "string",
                                                        "description", "WHERE条件，格式: field=value"
                                                )
                                        ),
                                        "required", new String[]{"database", "tableName", "setClause", "whereClause"}
                                )
                        ),
                        Map.of(
                                "name", "deleteData",
                                "description", "删除指定表的数据",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "database", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                ),
                                                "tableName", Map.of(
                                                        "type", "string",
                                                        "description", "表名"
                                                ),
                                                "whereClause", Map.of(
                                                        "type", "string",
                                                        "description", "WHERE条件，格式: field=value"
                                                )
                                        ),
                                        "required", new String[]{"database", "tableName", "whereClause"}
                                )
                        ),
                        Map.of(
                                "name", "createDB",
                                "description", "创建新数据库",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "databaseName", Map.of(
                                                        "type", "string",
                                                        "description", "数据库名称"
                                                )
                                        ),
                                        "required", new String[]{"databaseName"}
                                )
                        ),
                        Map.of(
                                "name", "checkCriticalTransactions",
                                "description", "检查当前是否有活跃的大事务或长事务",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        ),
                        Map.of(
                                "name", "avgSQLRT",
                                "description", "计算SQL请求平均响应耗时",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        ),
                        Map.of(
                                "name", "listNotableWaitEvents",
                                "description", "检查需要关注的数据库等待事件",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        ),
                        Map.of(
                                "name", "checkMGRStatus",
                                "description", "监控MGR集群状态",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        ),
                        Map.of(
                                "name", "findAbnormalMemoryIssue",
                                "description", "检查数据库中是否存在内存异常情况",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        ),
                        Map.of(
                                "name", "findImproperVars",
                                "description", "检查数据库系统参数配置是否合理",
                                "inputSchema", Map.of(
                                        "type", "object",
                                        "properties", Map.of(),
                                        "required", new String[]{}
                                )
                        )
                }
        );
    }
    private Object handleToolsCall(Map<String, Object> params) throws Exception {
        if (params == null) {
            return Map.of("error", "参数不能为空");
        }

        String name = (String) params.get("name");
        if (name == null) {
            return Map.of("error", "工具名称不能为空");
        }

        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (arguments == null) {
            arguments = Map.of();
        }

        Object result = switch (name) {
            case "listDatabases" -> databaseService.listDatabases();
            case "avgSQLRT" -> databaseService.avgSQLRT();
            case "listTables" -> {
                String database = (String) arguments.get("database");
                if (database == null) {
                    yield Map.of("error", "数据库名称不能为空");
                }
                yield databaseService.listTables(database);
            }
            case "executeQuery" -> {
                String database = (String) arguments.get("database");
                String query = (String) arguments.get("query");
                if (database == null || query == null) {
                    yield Map.of("error", "数据库名称和查询语句不能为空");
                }
                yield databaseService.executeQuery(database, query);
            }
            case "getTableRowCount" -> {
                String database = (String) arguments.get("database");
                String tableName = (String) arguments.get("tableName");
                if (database == null || tableName == null) {
                    yield Map.of("error", "数据库名称和表名不能为空");
                }
                yield databaseService.getTableRowCount(database, tableName);
            }
            case "insertData" -> {
                String database = (String) arguments.get("database");
                String tableName = (String) arguments.get("tableName");
                String columns = (String) arguments.get("columns");
                String values = (String) arguments.get("values");
                if (database == null || tableName == null || columns == null || values == null) {
                    yield Map.of("error", "所有参数都不能为空");
                }
                yield databaseService.insertData(database, tableName, columns, values);
            }
            case "updateData" -> {
                String database = (String) arguments.get("database");
                String tableName = (String) arguments.get("tableName");
                String setClause = (String) arguments.get("setClause");
                String whereClause = (String) arguments.get("whereClause");
                if (database == null || tableName == null || setClause == null || whereClause == null) {
                    yield Map.of("error", "所有参数都不能为空");
                }
                yield databaseService.updateData(database, tableName, setClause, whereClause);
            }
            case "deleteData" -> {
                String database = (String) arguments.get("database");
                String tableName = (String) arguments.get("tableName");
                String whereClause = (String) arguments.get("whereClause");
                if (database == null || tableName == null || whereClause == null) {
                    yield Map.of("error", "所有参数都不能为空");
                }
                yield databaseService.deleteData(database, tableName, whereClause);
            }
            case "createDB" -> {
                String databaseName = (String) arguments.get("databaseName");
                if (databaseName == null) {
                    yield Map.of("error", "数据库名称不能为空");
                }
                yield databaseService.createDB(databaseName);
            }
            case "checkCriticalTransactions" -> {
                yield databaseService.checkCriticalTransactions();
            }
            case "listNotableWaitEvents" -> {
                yield databaseService.listNotableWaitEvents();
            }
            case "checkMGRStatus" -> {
                yield databaseService.checkMGRStatus();
            }
            case "findAbnormalMemoryIssue" -> {
                yield databaseService.findAbnormalMemoryIssue();
            }
            case "findImproperVars" -> {
                yield databaseService.findImproperVars();
            }
            default -> Map.of("error", "未知的工具: " + name);
        };
        return Map.of(
                "content", new Object[]{
                        Map.of(
                                "type", "text",
                                "text", objectMapper.writeValueAsString(result)
                        )
                }
        );
    }

}
