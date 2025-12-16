package com.aiexploration.chat.service;

import com.aiexploration.chat.model.ToolCallInfo;
import com.aiexploration.chat.model.mcp.McpServerConfig;
import com.aiexploration.chat.model.mcp.McpTool;
import com.aiexploration.chat.model.mcp.McpToolExecutionRequest;
import com.aiexploration.chat.model.mcp.McpToolExecutionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sashirestela.openai.common.tool.Tool;
import io.github.sashirestela.openai.common.tool.ToolCall;
import io.github.sashirestela.openai.common.tool.ToolType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class McpFunctionExecutor {

    private final McpClientService mcpClientService;
    private final McpServerManager mcpServerManager;
    private final ObjectMapper objectMapper;

    private final Map<String, McpTool> toolsRegistry = new ConcurrentHashMap<>();

    public McpFunctionExecutor(
            McpClientService mcpClientService,
            McpServerManager mcpServerManager,
            ObjectMapper objectMapper
    ) {
        this.mcpClientService = mcpClientService;
        this.mcpServerManager = mcpServerManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        loadAllTools();
    }

    private void loadAllTools() {
        try {
            List<McpTool> tools = mcpServerManager.getAllTools();
            tools.forEach(tool -> toolsRegistry.put(tool.getName(), tool));
            log.info("Loaded {} MCP tools for function calling", tools.size());
            tools.forEach(tool -> log.debug("  - {}: {}", tool.getName(), tool.getDescription()));
        } catch (Exception e) {
            log.error("Failed to load MCP tools", e);
        }
    }

    /**
     * Get tool functions in OpenAI format for ChatRequest
     */
    public List<Tool> getToolFunctions() {
        if (toolsRegistry.isEmpty()) {
            log.warn("No MCP tools available, reloading...");
            loadAllTools();
        }

        return toolsRegistry.values().stream()
                .map(this::convertToToolFunction)
                .collect(Collectors.toList());
    }

    /**
     * Convert MCP tool to OpenAI Tool object
     */
    private Tool convertToToolFunction(McpTool tool) {
        // Convert Map to JsonNode for the parameters
        JsonNode parametersNode = objectMapper.valueToTree(tool.getInputSchema());

        var functionDef = new Tool.ToolFunctionDef(
                tool.getName(),
                tool.getDescription(),
                parametersNode,
                null  // strict parameter (optional)
        );

        return new Tool(ToolType.FUNCTION, functionDef);
    }

    /**
     * Execute a single tool call
     */
    public ToolCallInfo executeTool(ToolCall toolCall) {
        long startTime = System.currentTimeMillis();

        String toolName = toolCall.getFunction().getName();
        String argumentsJson = toolCall.getFunction().getArguments();

        log.info("Executing tool call: {} with arguments: {}", toolName, argumentsJson);

        try {
            // Parse arguments from JSON string
            Map<String, Object> arguments = objectMapper.readValue(
                    argumentsJson,
                    new TypeReference<Map<String, Object>>() {}
            );

            // Get tool from registry
            McpTool tool = toolsRegistry.get(toolName);
            if (tool == null) {
                throw new RuntimeException("Tool not found: " + toolName);
            }

            // Get server for this tool
            McpServerConfig server = mcpServerManager.getServer(tool.getServerId());
            if (server == null) {
                throw new RuntimeException("Server not found for tool: " + tool.getServerId());
            }

            // Execute via MCP client
            McpToolExecutionRequest request = McpToolExecutionRequest.builder()
                    .serverId(tool.getServerId())
                    .toolName(toolName)
                    .arguments(arguments)
                    .build();

            McpToolExecutionResponse response = mcpClientService.executeTool(server, request);

            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Tool {} execution completed in {}ms, success: {}",
                    toolName, executionTime, response.isSuccess());

            return ToolCallInfo.builder()
                    .toolCallId(toolCall.getId())
                    .toolName(toolName)
                    .arguments(arguments)
                    .result(response.getContent())
                    .executionTimeMs(executionTime)
                    .success(response.isSuccess())
                    .error(response.getError())
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Tool execution failed: {}", toolName, e);

            return ToolCallInfo.builder()
                    .toolCallId(toolCall.getId())
                    .toolName(toolName)
                    .executionTimeMs(executionTime)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Execute all tool calls (can be parallelized in the future)
     */
    public List<ToolCallInfo> executeAll(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Executing {} tool calls", toolCalls.size());

        // Sequential execution (can be made parallel with CompletableFuture.allOf)
        return toolCalls.stream()
                .map(this::executeTool)
                .collect(Collectors.toList());
    }

    /**
     * Refresh tools registry
     */
    public void refreshTools() {
        log.info("Refreshing MCP tools registry");
        toolsRegistry.clear();
        loadAllTools();
    }
}
