package com.aiexploration.mcp.weather.controller;

import com.aiexploration.mcp.weather.model.mcp.JsonRpcRequest;
import com.aiexploration.mcp.weather.model.mcp.JsonRpcResponse;
import com.aiexploration.mcp.weather.model.mcp.ToolDefinition;
import com.aiexploration.mcp.weather.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerController {

    private final McpToolService mcpToolService;
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    @PostMapping(
            value = "",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, "text/event-stream"}
    )
    public ResponseEntity<?> handleMcpRequest(
            @RequestBody JsonRpcRequest request,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String sessionId) {

        log.info("Received MCP request: method={}, id={}, sessionId={}",
                request.getMethod(), request.getId(), sessionId);

        try {
            Object result = switch (request.getMethod()) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                case "ping" -> handlePing(request);
                default -> null;
            };

            if (result == null) {
                return ResponseEntity.ok(JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .error(JsonRpcResponse.JsonRpcError.builder()
                                .code(-32601)
                                .message("Method not found: " + request.getMethod())
                                .build())
                        .build());
            }

            // For initialize method, add session ID header
            if ("initialize".equals(request.getMethod())) {
                String newSessionId = UUID.randomUUID().toString();
                sessions.put(newSessionId, newSessionId);

                HttpHeaders headers = new HttpHeaders();
                headers.add("Mcp-Session-Id", newSessionId);

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(JsonRpcResponse.builder()
                                .jsonrpc("2.0")
                                .id(request.getId())
                                .result(result)
                                .build());
            }

            return ResponseEntity.ok(JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(request.getId())
                    .result(result)
                    .build());

        } catch (Exception e) {
            log.error("Error handling MCP request", e);
            return ResponseEntity.ok(JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(request.getId())
                    .error(JsonRpcResponse.JsonRpcError.builder()
                            .code(-32603)
                            .message("Internal error: " + e.getMessage())
                            .build())
                    .build());
        }
    }

    private Object handleInitialize(JsonRpcRequest request) {
        log.info("Handling initialize request");

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "weather-mcp-server");
        serverInfo.put("version", "1.0.0");

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);

        return result;
    }

    private Object handleToolsList(JsonRpcRequest request) {
        log.info("Handling tools/list request");

        List<ToolDefinition> tools = mcpToolService.getTools();

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);

        return result;
    }

    private Object handleToolsCall(JsonRpcRequest request) {
        log.info("Handling tools/call request");

        Map<String, Object> params = request.getParams();
        if (params == null) {
            throw new IllegalArgumentException("Missing params");
        }

        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());

        if (toolName == null) {
            throw new IllegalArgumentException("Missing tool name");
        }

        return mcpToolService.executeTool(toolName, arguments);
    }

    private Object handlePing(JsonRpcRequest request) {
        log.info("Handling ping request");
        return Map.of("status", "ok");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "weather-mcp-server"
        ));
    }
}
