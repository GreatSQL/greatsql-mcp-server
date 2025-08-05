package org.greatsql.greatsqlmcp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TableInfo {
    private String tableName;
    private String tableSchema;
    private Long tableRows;
    private String createTime;

    public TableInfo(String tableName, String tableSchema,
                     Long tableRows, String createTime) {
        this.tableName = tableName;
        this.tableSchema = tableSchema;
        this.tableRows = tableRows;
        this.createTime = createTime;
    }
}