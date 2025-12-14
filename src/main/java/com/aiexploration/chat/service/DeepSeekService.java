package com.aiexploration.chat.service;

import com.aiexploration.chat.model.ChatRequest;
import com.aiexploration.chat.model.ChatResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeepSeekService implements AIService {

    private final DeepSeekChatModel chatModel;
    private final HistorySummarizationService summarizationService;

    public DeepSeekService(DeepSeekChatModel chatModel, HistorySummarizationService summarizationService) {
        this.chatModel = chatModel;
        this.summarizationService = summarizationService;
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
            Boolean autoSummarize
    ) {
        String historyKey = "conversationHistory_deepseek";

        // Build messages list
        List<Message> messages = new ArrayList<>();

        // Add system prompt if provided
        if ("custom".equalsIgnoreCase(systemPromptType) &&
                customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
            messages.add(new SystemMessage(customSystemPrompt));
        }

        // Load conversation history
        @SuppressWarnings("unchecked")
        List<ChatRequest.Message> history = (List<ChatRequest.Message>) session.getAttribute(historyKey);

        if (history != null) {
            messages.addAll(convertToSpringAIMessages(history));
        } else {
            history = new ArrayList<>();
        }

        // Add current user message
        messages.add(new UserMessage(userMessage));

        // Configure options
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(model != null ? model : "deepseek-chat")
                .temperature(temperature != null ? temperature : 0.7)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .build();

        // Measure execution time
        long startTime = System.currentTimeMillis();

        // Call Spring AI
        org.springframework.ai.chat.model.ChatResponse aiResponse =
                chatModel.call(new Prompt(messages, options));

        long executionTime = System.currentTimeMillis() - startTime;

        // Extract response data
        String assistantContent = aiResponse.getResult().getOutput().getText();
        String finishReason = aiResponse.getResult().getMetadata().getFinishReason();

        // Extract token usage
        var usage = aiResponse.getMetadata().getUsage();
        ChatResponse.Usage tokenUsage = ChatResponse.Usage.builder()
                .promptTokens(usage != null ? usage.getPromptTokens() : 0)
                .completionTokens(usage != null ? usage.getCompletionTokens() : 0)
                .totalTokens(usage != null ? usage.getTotalTokens() : 0)
                .build();

        // Update history
        history.add(ChatRequest.Message.builder()
                .role("user").content(userMessage).build());
        history.add(ChatRequest.Message.builder()
                .role("assistant").content(assistantContent).build());

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
                .content(assistantContent)
                .executionTimeMs(executionTime)
                .usage(tokenUsage)
                .finishReason(finishReason)
                .summarizationInfo(summarizationInfo)
                .build();
    }

    private List<Message> convertToSpringAIMessages(List<ChatRequest.Message> history) {
        return history.stream()
                .map(msg -> {
                    if ("system".equals(msg.getRole())) {
                        return new SystemMessage(msg.getContent());
                    } else if ("user".equals(msg.getRole())) {
                        return new UserMessage(msg.getContent());
                    } else {
                        return new AssistantMessage(msg.getContent());
                    }
                })
                .collect(Collectors.toList());
    }
}
