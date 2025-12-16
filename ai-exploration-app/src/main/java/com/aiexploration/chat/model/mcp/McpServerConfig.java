package com.aiexploration.chat.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {
    private String id;
    private String name;
    private String url;
    private String description;
    private Map<String, String> headers;
    private boolean enabled;
    private LocalDateTime createdAt;
    private String sessionId; // MCP session ID obtained during initialization
}
