package org.greatsql.greatsqlmcp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AuthConfig {
    @Value("${mcp.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${mcp.auth.api-key:}")
    private String apiKey;
}