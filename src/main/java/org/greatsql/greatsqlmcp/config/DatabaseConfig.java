package org.greatsql.greatsqlmcp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class DatabaseConfig {
    @Value("${greatsql.url}")
    private String jdbcUrl;

    @Value("${greatsql.user}")
    private String username;

    @Value("${greatsql.password}")
    private String password;
}