package com.aiexploration.chat.service;

import com.aiexploration.chat.model.mcp.McpServerConfig;
import com.aiexploration.chat.model.mcp.McpTool;
import com.aiexploration.chat.model.mcp.McpToolExecutionRequest;
import com.aiexploration.chat.model.mcp.McpToolExecutionResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class McpServerManager {

    private final Map<String, McpServerConfig> servers = new ConcurrentHashMap<>();
    private final Map<String, List<McpTool>> serverTools = new ConcurrentHashMap<>();

    private final McpClientService mcpClient;

    @Autowired(required = false)
    private McpServerConfig defaultMcpServer;

    public McpServerManager(McpClientService mcpClient) {
        this.mcpClient = mcpClient;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing MCP Server Manager");

        // Load default server from configuration
        if (defaultMcpServer != null) {
            log.info("Adding default MCP server: {}", defaultMcpServer.getName());
            addServer(defaultMcpServer);
        } else {
            log.info("No default MCP server configured");
        }
    }

    public McpServerConfig addServer(McpServerConfig config) {
        // Validate config
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            throw new IllegalArgumentException("Server URL is required");
        }

        // Generate ID if not provided
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(UUID.randomUUID().toString());
        }

        // Set creation time if not set
        if (config.getCreatedAt() == null) {
            config.setCreatedAt(LocalDateTime.now());
        }

        // Set enabled to true by default
        config.setEnabled(true);

        // Test connection
        boolean connectionSuccessful = mcpClient.initializeConnection(config);
        if (!connectionSuccessful) {
            log.warn("Failed to initialize connection to MCP server: {}", config.getName());
        }

        // Add to servers map
        servers.put(config.getId(), config);
        log.info("Added MCP server: {} (id: {})", config.getName(), config.getId());

        // Try to load tools immediately
        if (connectionSuccessful) {
            refreshServerTools(config.getId());
        }

        return config;
    }

    public void removeServer(String serverId) {
        McpServerConfig removed = servers.remove(serverId);
        serverTools.remove(serverId);

        if (removed != null) {
            log.info("Removed MCP server: {} (id: {})", removed.getName(), serverId);
        } else {
            log.warn("Attempted to remove non-existent server: {}", serverId);
        }
    }

    public McpServerConfig getServer(String serverId) {
        return servers.get(serverId);
    }

    public List<McpServerConfig> getAllServers() {
        return new ArrayList<>(servers.values());
    }

    public List<McpTool> refreshServerTools(String serverId) {
        McpServerConfig server = servers.get(serverId);
        if (server == null) {
            log.warn("Cannot refresh tools for non-existent server: {}", serverId);
            return Collections.emptyList();
        }

        if (!server.isEnabled()) {
            log.warn("Cannot refresh tools for disabled server: {}", server.getName());
            return Collections.emptyList();
        }

        log.info("Refreshing tools for MCP server: {}", server.getName());
        List<McpTool> tools = mcpClient.listTools(server);
        serverTools.put(serverId, tools);

        return tools;
    }

    public List<McpTool> getToolsForServer(String serverId) {
        return serverTools.getOrDefault(serverId, Collections.emptyList());
    }

    public List<McpTool> getAllTools() {
        return serverTools.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public McpToolExecutionResponse executeTool(McpToolExecutionRequest request) {
        // Validate request
        if (request.getServerId() == null || request.getServerId().isEmpty()) {
            return McpToolExecutionResponse.builder()
                    .success(false)
                    .error("Server ID is required")
                    .build();
        }

        if (request.getToolName() == null || request.getToolName().isEmpty()) {
            return McpToolExecutionResponse.builder()
                    .success(false)
                    .error("Tool name is required")
                    .build();
        }

        // Get server
        McpServerConfig server = servers.get(request.getServerId());
        if (server == null) {
            return McpToolExecutionResponse.builder()
                    .success(false)
                    .error("Server not found: " + request.getServerId())
                    .build();
        }

        if (!server.isEnabled()) {
            return McpToolExecutionResponse.builder()
                    .success(false)
                    .error("Server is disabled: " + server.getName())
                    .build();
        }

        // Execute tool
        return mcpClient.executeTool(server, request);
    }

    public boolean testConnection(String serverId) {
        McpServerConfig server = servers.get(serverId);
        if (server == null) {
            log.warn("Cannot test connection for non-existent server: {}", serverId);
            return false;
        }

        return mcpClient.initializeConnection(server);
    }
}
