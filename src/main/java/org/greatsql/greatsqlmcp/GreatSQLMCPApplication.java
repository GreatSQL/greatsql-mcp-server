package org.greatsql.greatsqlmcp;

import org.greatsql.greatsqlmcp.service.DatabaseService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.List;

@SpringBootApplication
public class GreatSQLMCPApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreatSQLMCPApplication.class, args);
    }

    @Bean
    public List<ToolCallback> getToolCallbacks(DatabaseService databaseService) {
        return List.of(ToolCallbacks.from(databaseService));
    }
}
