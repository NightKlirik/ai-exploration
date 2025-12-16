package com.aiexploration.chat.service;

import com.aiexploration.chat.model.mcp.McpServerConfig;
import com.aiexploration.chat.model.mcp.McpTool;
import com.aiexploration.chat.model.mcp.McpToolExecutionRequest;
import com.aiexploration.chat.model.mcp.McpToolExecutionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public boolean initializeConnection(McpServerConfig serverConfig) {
        log.info("Initializing connection to MCP server: {}", serverConfig.getName());
        try {
            // Build JSON-RPC initialize request
            ObjectNode request = buildJsonRpcRequest("initialize", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of(
                            "name", "ai-exploration",
                            "version", "1.0.0"
                    )
            ));

            // Send request (without session ID for initialization)
            ResponseEntity<String> response = sendJsonRpcRequestWithResponse(serverConfig, request);

            // Parse response
            String responseBody = response.getBody();

            // Handle SSE format if needed
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && contentType.includes(MediaType.parseMediaType("text/event-stream"))) {
                responseBody = parseSseResponse(responseBody);
                log.debug("Extracted JSON from SSE during initialization: {}", responseBody);
            }

            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (jsonResponse.has("error")) {
                log.error("MCP server initialization failed: {}", jsonResponse.get("error"));
                return false;
            }

            // Extract session ID from response headers
            String sessionId = response.getHeaders().getFirst("Mcp-Session-Id");
            if (sessionId != null && !sessionId.isEmpty()) {
                serverConfig.setSessionId(sessionId);
                log.info("Received MCP session ID: {}", sessionId);
            } else {
                log.warn("No MCP-Session-Id header in initialization response");
            }

            log.info("Successfully initialized connection to MCP server: {}", serverConfig.getName());
            return true;

        } catch (Exception e) {
            log.error("Failed to initialize connection to MCP server: {}", serverConfig.getName(), e);
            return false;
        }
    }

    public List<McpTool> listTools(McpServerConfig serverConfig) {
        log.info("Listing tools from MCP server: {}", serverConfig.getName());
        try {
            // Build JSON-RPC tools/list request
            ObjectNode request = buildJsonRpcRequest("tools/list", Map.of());

            // Send request
            String response = sendJsonRpcRequest(serverConfig, request);

            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response);

            if (jsonResponse.has("error")) {
                log.error("Failed to list tools: {}", jsonResponse.get("error"));
                return Collections.emptyList();
            }

            // Extract tools from result
            JsonNode result = jsonResponse.get("result");
            if (result == null || !result.has("tools")) {
                log.warn("No tools found in response");
                return Collections.emptyList();
            }

            JsonNode toolsArray = result.get("tools");
            List<McpTool> tools = new ArrayList<>();

            for (JsonNode toolNode : toolsArray) {
                McpTool tool = McpTool.builder()
                        .name(toolNode.has("name") ? toolNode.get("name").asText() : "")
                        .description(toolNode.has("description") ? toolNode.get("description").asText() : "")
                        .inputSchema(toolNode.has("inputSchema") ?
                                objectMapper.convertValue(toolNode.get("inputSchema"), Map.class) :
                                new HashMap<>())
                        .serverId(serverConfig.getId())
                        .build();
                tools.add(tool);
            }

            log.info("Found {} tools from MCP server: {}", tools.size(), serverConfig.getName());
            return tools;

        } catch (Exception e) {
            log.error("Failed to list tools from MCP server: {}", serverConfig.getName(), e);
            return Collections.emptyList();
        }
    }

    public McpToolExecutionResponse executeTool(McpServerConfig serverConfig, McpToolExecutionRequest request) {
        log.info("Executing tool '{}' on MCP server: {}", request.getToolName(), serverConfig.getName());
        long startTime = System.currentTimeMillis();

        try {
            // Build JSON-RPC tools/call request
            ObjectNode jsonRpcRequest = buildJsonRpcRequest("tools/call", Map.of(
                    "name", request.getToolName(),
                    "arguments", request.getArguments() != null ? request.getArguments() : Map.of()
            ));

            // Send request
            String response = sendJsonRpcRequest(serverConfig, jsonRpcRequest);

            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response);

            long executionTime = System.currentTimeMillis() - startTime;

            if (jsonResponse.has("error")) {
                JsonNode error = jsonResponse.get("error");
                String errorMessage = error.has("message") ? error.get("message").asText() : "Unknown error";

                log.error("Tool execution failed: {}", errorMessage);

                return McpToolExecutionResponse.builder()
                        .success(false)
                        .error(errorMessage)
                        .executionTimeMs(executionTime)
                        .build();
            }

            // Extract result
            JsonNode result = jsonResponse.get("result");
            Object content = result != null ? objectMapper.convertValue(result, Object.class) : null;

            log.info("Tool '{}' executed successfully in {}ms", request.getToolName(), executionTime);

            return McpToolExecutionResponse.builder()
                    .success(true)
                    .content(content)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to execute tool '{}' on MCP server: {}", request.getToolName(), serverConfig.getName(), e);

            return McpToolExecutionResponse.builder()
                    .success(false)
                    .error(e.getMessage())
                    .executionTimeMs(executionTime)
                    .build();
        }
    }

    private ObjectNode buildJsonRpcRequest(String method, Object params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString());
        request.put("method", method);
        request.set("params", objectMapper.valueToTree(params));
        return request;
    }

    private String sendJsonRpcRequest(McpServerConfig serverConfig, ObjectNode request) throws Exception {
        ResponseEntity<String> response = sendJsonRpcRequestWithResponse(serverConfig, request);

        String responseBody = response.getBody();
        log.debug("Received response with Content-Type: {}", response.getHeaders().getContentType());
        log.debug("Received raw response: {}", responseBody);

        // Check if response is SSE format (text/event-stream)
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && contentType.includes(MediaType.parseMediaType("text/event-stream"))) {
            // Parse SSE format to extract JSON data
            responseBody = parseSseResponse(responseBody);
            log.debug("Extracted JSON from SSE: {}", responseBody);
        }

        return responseBody;
    }

    private ResponseEntity<String> sendJsonRpcRequestWithResponse(McpServerConfig serverConfig, ObjectNode request) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Set Accept header to include both application/json and text/event-stream
        // as required by MCP servers
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.parseMediaType("text/event-stream")));

        // Add MCP session ID header if available
        if (serverConfig.getSessionId() != null && !serverConfig.getSessionId().isEmpty()) {
            headers.add("Mcp-Session-Id", serverConfig.getSessionId());
            log.debug("Adding Mcp-Session-Id header: {}", serverConfig.getSessionId());
        }

        // Add custom headers (e.g., Authorization)
        if (serverConfig.getHeaders() != null) {
            serverConfig.getHeaders().forEach(headers::add);
        }

        String requestBody = objectMapper.writeValueAsString(request);
        log.debug("Sending JSON-RPC request to {}: {}", serverConfig.getUrl(), requestBody);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity(serverConfig.getUrl(), entity, String.class);
    }

    private String parseSseResponse(String sseResponse) {
        if (sseResponse == null || sseResponse.isEmpty()) {
            return "{}";
        }

        // SSE format: event: <type>\ndata: <json>\n\n
        // We need to extract the JSON from the data: line
        String[] lines = sseResponse.split("\n");
        StringBuilder jsonData = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                jsonData.append(data);
            }
        }

        String result = jsonData.toString();
        return result.isEmpty() ? "{}" : result;
    }
}
