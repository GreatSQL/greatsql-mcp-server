package org.greatsql.greatsqlmcp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DatabaseInfo {
    private String databaseName;

    public DatabaseInfo(String databaseName) {
        this.databaseName = databaseName;
    }
}