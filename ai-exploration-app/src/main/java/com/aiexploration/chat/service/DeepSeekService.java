package com.aiexploration.chat.service;

import com.aiexploration.chat.model.ChatRequest;
import com.aiexploration.chat.model.ChatResponse;
import com.aiexploration.chat.model.ToolCallInfo;
import io.github.sashirestela.openai.SimpleOpenAIDeepseek;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.AssistantMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.SystemMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.ToolMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.UserMessage;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeepSeekService implements AIService {

    private final SimpleOpenAIDeepseek deepSeekClient;
    private final HistorySummarizationService summarizationService;
    private final McpFunctionExecutor mcpFunctionExecutor;

    @Value("${deepseek.default.model:deepseek-chat}")
    private String defaultModel;

    @Value("${deepseek.default.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${deepseek.default.max-tokens:2000}")
    private Integer defaultMaxTokens;

    @Value("${function-calling.max-iterations:5}")
    private int maxFunctionCallingIterations;

    public DeepSeekService(
            @Qualifier("deepSeekClient") SimpleOpenAIDeepseek deepSeekClient,
            HistorySummarizationService summarizationService,
            McpFunctionExecutor mcpFunctionExecutor
    ) {
        this.deepSeekClient = deepSeekClient;
        this.summarizationService = summarizationService;
        this.mcpFunctionExecutor = mcpFunctionExecutor;
    }

    @Override
    public ChatResponse chat(
            String userMessage,
            String model,
            String format,
            Double temperature,
            Integer maxTokens,
            String systemPromptType,
            String customSystemPrompt,
            HttpSession session,
            Boolean autoSummarize,
            Boolean enableFunctionCalling
    ) {
        String historyKey = "conversationHistory_deepseek";

        // Build messages list
        List<ChatMessage> messages = new ArrayList<>();

        // Add system prompt if provided
        if ("custom".equalsIgnoreCase(systemPromptType) &&
                customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
            messages.add(SystemMessage.of(customSystemPrompt));
        }

        // Load conversation history
        @SuppressWarnings("unchecked")
        List<ChatRequest.Message> history = (List<ChatRequest.Message>) session.getAttribute(historyKey);

        if (history != null) {
            messages.addAll(convertToChatMessages(history));
        } else {
            history = new ArrayList<>();
        }

        // Add current user message
        messages.add(UserMessage.of(userMessage));

        // Track all tool calls for response
        List<ToolCallInfo> allToolCalls = new ArrayList<>();

        // Measure total execution time
        long startTime = System.currentTimeMillis();

        // Execute chat with function calling loop
        Chat finalResult;
        String finalContent;
        String finalFinishReason;

        if (Boolean.TRUE.equals(enableFunctionCalling)) {
            log.info("Function calling enabled for DeepSeek, will use MCP tools if needed");

            int iteration = 0;
            Chat currentResult = null;

            while (iteration < maxFunctionCallingIterations) {
                log.debug("Function calling iteration {}/{}", iteration + 1, maxFunctionCallingIterations);

                // Build request with tools
                var requestBuilder = io.github.sashirestela.openai.domain.chat.ChatRequest.builder()
                        .model(model != null ? model : defaultModel)
                        .messages(messages)
                        .temperature(temperature != null ? temperature : defaultTemperature)
                        .maxCompletionTokens(maxTokens != null ? maxTokens : defaultMaxTokens);

                // Add tools on first iteration or if we're continuing after tool execution
                if (iteration == 0 || (currentResult != null && "tool_calls".equals(currentResult.getChoices().get(0).getFinishReason()))) {
                    try {
                        var tools = mcpFunctionExecutor.getToolFunctions();
                        if (!tools.isEmpty()) {
                            requestBuilder.tools(tools);
                            log.debug("Added {} tools to request", tools.size());
                        }
                    } catch (Exception e) {
                        log.error("Failed to get MCP tools, continuing without function calling", e);
                    }
                }

                var request = requestBuilder.build();

                // Call DeepSeek (using OpenAI-compatible API)
                currentResult = deepSeekClient.chatCompletions().create(request).join();
                String finishReason = currentResult.getChoices().get(0).getFinishReason();

                log.debug("API response finish_reason: {}", finishReason);

                // Check if model wants to call tools
                if ("tool_calls".equals(finishReason)) {
                    var responseMessage = currentResult.getChoices().get(0).getMessage();
                    var toolCalls = responseMessage.getToolCalls();

                    if (toolCalls == null || toolCalls.isEmpty()) {
                        log.warn("finish_reason is tool_calls but no tool_calls in response, breaking loop");
                        break;
                    }

                    log.info("Model requested {} tool call(s)", toolCalls.size());

                    // Add assistant message with tool_calls to history
                    messages.add(responseMessage);

                    // Execute all tool calls
                    List<ToolCallInfo> executedTools = mcpFunctionExecutor.executeAll(toolCalls);
                    allToolCalls.addAll(executedTools);

                    // Add ToolMessage for each result
                    for (ToolCallInfo toolInfo : executedTools) {
                        String resultContent;
                        if (Boolean.TRUE.equals(toolInfo.getSuccess())) {
                            resultContent = formatToolResult(toolInfo.getResult());
                            log.debug("Tool {} succeeded: {}", toolInfo.getToolName(),
                                    resultContent.length() > 100 ? resultContent.substring(0, 100) + "..." : resultContent);
                        } else {
                            resultContent = "Error: " + toolInfo.getError();
                            log.error("Tool {} failed: {}", toolInfo.getToolName(), toolInfo.getError());
                        }

                        messages.add(ToolMessage.of(resultContent, toolInfo.getToolCallId()));
                    }

                    iteration++;
                    continue;
                }

                // Model finished without tool calls
                break;
            }

            if (iteration >= maxFunctionCallingIterations) {
                log.warn("Reached maximum function calling iterations ({})", maxFunctionCallingIterations);
            }

            finalResult = currentResult;
            finalContent = finalResult != null ? finalResult.firstContent() : "";
            finalFinishReason = finalResult != null ? finalResult.getChoices().get(0).getFinishReason() : "unknown";

        } else {
            // No function calling - simple request
            log.debug("Function calling disabled");

            var request = io.github.sashirestela.openai.domain.chat.ChatRequest.builder()
                    .model(model != null ? model : defaultModel)
                    .messages(messages)
                    .temperature(temperature != null ? temperature : defaultTemperature)
                    .maxCompletionTokens(maxTokens != null ? maxTokens : defaultMaxTokens)
                    .build();

            finalResult = deepSeekClient.chatCompletions().create(request).join();
            finalContent = finalResult.firstContent();
            finalFinishReason = finalResult.getChoices().get(0).getFinishReason();
        }

        long executionTime = System.currentTimeMillis() - startTime;

        // Extract token usage
        ChatResponse.Usage tokenUsage = ChatResponse.Usage.builder()
                .promptTokens(finalResult.getUsage() != null ? (int) finalResult.getUsage().getPromptTokens() : 0)
                .completionTokens(finalResult.getUsage() != null ? (int) finalResult.getUsage().getCompletionTokens() : 0)
                .totalTokens(finalResult.getUsage() != null ? (int) finalResult.getUsage().getTotalTokens() : 0)
                .build();

        // Update history
        history.add(ChatRequest.Message.builder()
                .role("user").content(userMessage).build());
        history.add(ChatRequest.Message.builder()
                .role("assistant").content(finalContent).build());

        // Handle auto-summarization
        ChatResponse.SummarizationInfo summarizationInfo = null;
        if (Boolean.TRUE.equals(autoSummarize) &&
                summarizationService.needsSummarization(history)) {

            String summary = summarizationService.createSummary(
                    history, this, model, temperature, maxTokens,
                    new MockHttpSession()
            );

            if (summary != null) {
                int count = summarizationService.applySummary(history, summary);
                summarizationInfo = ChatResponse.SummarizationInfo.builder()
                        .summarizationOccurred(true)
                        .messagesSummarized(count)
                        .summaryContent(summary)
                        .build();
            }
        }

        session.setAttribute(historyKey, history);

        // Build response
        return ChatResponse.builder()
                .content(finalContent)
                .executionTimeMs(executionTime)
                .usage(tokenUsage)
                .finishReason(finalFinishReason)
                .summarizationInfo(summarizationInfo)
                .toolCalls(allToolCalls.isEmpty() ? null : allToolCalls)
                .hadToolCalls(!allToolCalls.isEmpty())
                .build();
    }

    /**
     * Format tool result for sending back to the model
     */
    private String formatToolResult(Object result) {
        if (result == null) {
            return "null";
        }

        // If MCP result with content array
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            if (map.containsKey("content") && map.get("content") instanceof List) {
                List<?> content = (List<?>) map.get("content");
                return content.stream()
                        .filter(c -> c instanceof Map)
                        .map(c -> ((Map<?, ?>) c).get("text"))
                        .filter(text -> text != null)
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"));
            }
        }

        return String.valueOf(result);
    }

    private List<ChatMessage> convertToChatMessages(List<ChatRequest.Message> history) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (ChatRequest.Message msg : history) {
            if ("user".equals(msg.getRole())) {
                chatMessages.add(UserMessage.of(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                chatMessages.add(AssistantMessage.of(msg.getContent()));
            }
            // Note: tool messages from history are not re-added to avoid confusion
        }
        return chatMessages;
    }
}
