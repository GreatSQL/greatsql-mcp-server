package org.greatsql.greatsqlmcp.service;

import org.greatsql.greatsqlmcp.config.DatabaseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
public class ConnectionService {
    @Autowired
    private DatabaseConfig databaseConfig;

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                databaseConfig.getJdbcUrl(),
                databaseConfig.getUsername(),
                databaseConfig.getPassword()
        );
    }

    public Connection getConnection(String database) throws SQLException {
        String baseUrl = databaseConfig.getJdbcUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return DriverManager.getConnection(
                baseUrl + database,
                databaseConfig.getUsername(),
                databaseConfig.getPassword()
        );
    }
}
