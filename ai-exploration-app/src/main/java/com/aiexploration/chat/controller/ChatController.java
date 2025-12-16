package com.aiexploration.chat.controller;

import com.aiexploration.chat.model.ChatResponse;
import com.aiexploration.chat.service.AIService;
import com.aiexploration.chat.service.ConversationService;
import com.aiexploration.chat.service.DeepSeekService;
import com.aiexploration.chat.service.OpenAIService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final Map<String, AIService> services;
    private final ConversationService conversationService;

    public ChatController(
            OpenAIService openAIService,
            DeepSeekService deepSeekService,
            ConversationService conversationService
    ) {
        this.services = new HashMap<>();
        this.services.put("openai", openAIService);
        this.services.put("deepseek", deepSeekService);
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody Map<String, Object> request, HttpSession session) {
        String message = (String) request.get("message");
        String model = (String) request.get("model");
        String format = (String) request.get("format");
        Double temperature = request.get("temperature") != null ? ((Number) request.get("temperature")).doubleValue() : null;
        Integer maxTokens = request.get("maxTokens") != null ? ((Number) request.get("maxTokens")).intValue() : null;
        String systemPromptType = (String) request.get("systemPromptType");
        String customSystemPrompt = (String) request.get("customSystemPrompt");
        String provider = (String) request.get("provider");
        Long conversationId = request.get("conversationId") != null ? ((Number) request.get("conversationId")).longValue() : null;
        Boolean enableFunctionCalling = request.get("enableFunctionCalling") != null
                ? (Boolean) request.get("enableFunctionCalling")
                : Boolean.FALSE;

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Default to openai if provider not specified
        if (provider == null || provider.trim().isEmpty()) {
            provider = "openai";
        }

        // Get autoSummarize from session first, then from request, default to false
        String autoSummarizeKey = "autoSummarize_" + provider.toLowerCase();
        Boolean autoSummarize = (Boolean) session.getAttribute(autoSummarizeKey);
        if (autoSummarize == null) {
            autoSummarize = request.get("autoSummarize") != null
                    ? (Boolean) request.get("autoSummarize")
                    : Boolean.FALSE;
        }

        // Get the appropriate service
        AIService service = services.get(provider.toLowerCase());
        if (service == null) {
            log.error("Unknown provider: {}", provider);
            return ResponseEntity.badRequest().build();
        }

        try {
            // Save user message to database if conversationId is provided
            if (conversationId != null) {
                conversationService.addMessage(conversationId, "user", message, null, null, null, null, null, false);
            }

            ChatResponse response = service.chat(message, model, format, temperature, maxTokens, systemPromptType, customSystemPrompt, session, autoSummarize, enableFunctionCalling);

            // Log token usage and finish reason
            if (response != null) {
                if (response.getUsage() != null) {
                    ChatResponse.Usage usage = response.getUsage();
                    log.info("Response - Provider: {}, Model: {}, Prompt: {} tokens, Completion: {} tokens, Total: {} tokens, Finish: {}, Execution time: {}ms",
                            provider, model,
                            usage.getPromptTokens(),
                            usage.getCompletionTokens(),
                            usage.getTotalTokens(),
                            response.getFinishReason(),
                            response.getExecutionTimeMs());

                    // Save assistant message to database if conversationId is provided
                    if (conversationId != null) {
                        conversationService.addMessage(
                                conversationId,
                                "assistant",
                                response.getContent(),
                                usage.getPromptTokens(),
                                usage.getCompletionTokens(),
                                usage.getTotalTokens(),
                                response.getExecutionTimeMs(),
                                response.getFinishReason(),
                                false
                        );
                    }
                } else {
                    log.warn("No token usage information available for provider: {}, model: {}", provider, model);

                    // Save assistant message even without usage info
                    if (conversationId != null) {
                        conversationService.addMessage(
                                conversationId,
                                "assistant",
                                response.getContent(),
                                null,
                                null,
                                null,
                                response.getExecutionTimeMs(),
                                response.getFinishReason(),
                                false
                        );
                    }
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error on handle message: {}, provider: {}, error: {}", message, provider, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/clear")
    public ResponseEntity<Void> clearHistory(@RequestBody(required = false) Map<String, Object> request, HttpSession session) {
        String provider = request != null ? (String) request.get("provider") : null;

        if (provider != null && !provider.trim().isEmpty()) {
            // Clear history for specific provider
            String historyKey = "conversationHistory_" + provider.toLowerCase();
            session.removeAttribute(historyKey);
        } else {
            // Clear all history
            session.removeAttribute("conversationHistory_openai");
            session.removeAttribute("conversationHistory_deepseek");
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/summarization/toggle")
    public ResponseEntity<Map<String, Object>> toggleSummarization(
            @RequestBody Map<String, Object> request,
            HttpSession session
    ) {
        String provider = (String) request.get("provider");
        Boolean enabled = (Boolean) request.get("enabled");

        if (provider == null || enabled == null) {
            return ResponseEntity.badRequest().build();
        }

        String key = "autoSummarize_" + provider.toLowerCase();
        session.setAttribute(key, enabled);

        Map<String, Object> response = new HashMap<>();
        response.put("provider", provider);
        response.put("enabled", enabled);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/summarization/status")
    public ResponseEntity<Map<String, Object>> getSummarizationStatus(
            @RequestParam String provider,
            HttpSession session
    ) {
        String key = "autoSummarize_" + provider.toLowerCase();
        Boolean enabled = (Boolean) session.getAttribute(key);

        Map<String, Object> response = new HashMap<>();
        response.put("provider", provider);
        response.put("enabled", enabled != null ? enabled : false);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/restore-history")
    public ResponseEntity<Void> restoreHistory(
            @RequestBody Map<String, Object> request,
            HttpSession session
    ) {
        Long conversationId = request.get("conversationId") != null
            ? ((Number) request.get("conversationId")).longValue()
            : null;
        String provider = (String) request.get("provider");

        if (conversationId == null || provider == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Load messages from database
            var messages = conversationService.getMessages(conversationId);

            // Build history for session using ChatRequest.Message
            String historyKey = "conversationHistory_" + provider.toLowerCase();
            java.util.List<com.aiexploration.chat.model.ChatRequest.Message> history = new java.util.ArrayList<>();

            for (var msg : messages) {
                // Skip summary messages from history
                if (Boolean.TRUE.equals(msg.getIsSummary())) {
                    continue;
                }

                history.add(com.aiexploration.chat.model.ChatRequest.Message.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .build());
            }

            session.setAttribute(historyKey, history);
            log.info("Restored {} messages to session for conversation {}", history.size(), conversationId);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error restoring history: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
