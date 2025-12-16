package com.aiexploration.chat.controller;

import com.aiexploration.chat.model.mcp.McpServerConfig;
import com.aiexploration.chat.model.mcp.McpTool;
import com.aiexploration.chat.model.mcp.McpToolExecutionRequest;
import com.aiexploration.chat.model.mcp.McpToolExecutionResponse;
import com.aiexploration.chat.service.McpServerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpServerManager serverManager;

    // ========== Server Management Endpoints ==========

    @GetMapping("/servers")
    public ResponseEntity<List<McpServerConfig>> listServers() {
        log.info("Listing all MCP servers");
        List<McpServerConfig> servers = serverManager.getAllServers();
        return ResponseEntity.ok(servers);
    }

    @PostMapping("/servers")
    public ResponseEntity<McpServerConfig> addServer(@RequestBody McpServerConfig config) {
        log.info("Adding MCP server: {}", config.getName());
        try {
            McpServerConfig added = serverManager.addServer(config);
            return ResponseEntity.status(HttpStatus.CREATED).body(added);
        } catch (IllegalArgumentException e) {
            log.error("Invalid server configuration: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to add MCP server", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/servers/{serverId}")
    public ResponseEntity<McpServerConfig> getServer(@PathVariable String serverId) {
        log.info("Getting MCP server: {}", serverId);
        McpServerConfig server = serverManager.getServer(serverId);

        if (server == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(server);
    }

    @DeleteMapping("/servers/{serverId}")
    public ResponseEntity<Void> removeServer(@PathVariable String serverId) {
        log.info("Removing MCP server: {}", serverId);
        serverManager.removeServer(serverId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/servers/{serverId}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String serverId) {
        log.info("Testing connection to MCP server: {}", serverId);

        McpServerConfig server = serverManager.getServer(serverId);
        if (server == null) {
            return ResponseEntity.notFound().build();
        }

        boolean success = serverManager.testConnection(serverId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("serverId", serverId);
        result.put("serverName", server.getName());
        result.put("message", success ? "Connection successful" : "Connection failed");

        return ResponseEntity.ok(result);
    }

    // ========== Tool Management Endpoints ==========

    @GetMapping("/tools")
    public ResponseEntity<List<McpTool>> listAllTools() {
        log.info("Listing all MCP tools");
        List<McpTool> tools = serverManager.getAllTools();
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/servers/{serverId}/tools")
    public ResponseEntity<List<McpTool>> listServerTools(@PathVariable String serverId) {
        log.info("Listing tools for MCP server: {}", serverId);

        McpServerConfig server = serverManager.getServer(serverId);
        if (server == null) {
            return ResponseEntity.notFound().build();
        }

        List<McpTool> tools = serverManager.getToolsForServer(serverId);
        return ResponseEntity.ok(tools);
    }

    @PostMapping("/servers/{serverId}/tools/refresh")
    public ResponseEntity<List<McpTool>> refreshTools(@PathVariable String serverId) {
        log.info("Refreshing tools for MCP server: {}", serverId);

        McpServerConfig server = serverManager.getServer(serverId);
        if (server == null) {
            return ResponseEntity.notFound().build();
        }

        List<McpTool> tools = serverManager.refreshServerTools(serverId);
        return ResponseEntity.ok(tools);
    }

    // ========== Tool Execution Endpoint ==========

    @PostMapping("/tools/execute")
    public ResponseEntity<McpToolExecutionResponse> executeTool(@RequestBody McpToolExecutionRequest request) {
        log.info("Executing MCP tool: {} on server: {}", request.getToolName(), request.getServerId());

        try {
            McpToolExecutionResponse response = serverManager.executeTool(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute tool", e);

            McpToolExecutionResponse errorResponse = McpToolExecutionResponse.builder()
                    .success(false)
                    .error("Internal server error: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
