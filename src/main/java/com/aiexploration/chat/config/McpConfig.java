package com.aiexploration.chat.config;

import com.aiexploration.chat.model.mcp.McpServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "mcp")
@Data
public class McpConfig {
    private boolean enabled;
    private DefaultServer defaultServer;
    private Connection connection;
    private int requestTimeout;

    @Data
    public static class DefaultServer {
        private String url;
        private String name;
        private String apiKey;
    }

    @Data
    public static class Connection {
        private int timeout;
    }

    @Bean
    public McpServerConfig defaultMcpServer() {
        if (!enabled || defaultServer == null || defaultServer.getUrl() == null || defaultServer.getUrl().isEmpty()) {
            return null;
        }

        Map<String, String> headers = new HashMap<>();
        if (defaultServer.getApiKey() != null && !defaultServer.getApiKey().isEmpty()) {
            headers.put("Authorization", "Bearer " + defaultServer.getApiKey());
        }

        return McpServerConfig.builder()
                .id("default")
                .name(defaultServer.getName() != null ? defaultServer.getName() : "Default MCP Server")
                .url(defaultServer.getUrl())
                .description("Default MCP server from configuration")
                .headers(headers)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // Registers JavaTimeModule and other modules
        return mapper;
    }
}
