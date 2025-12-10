package com.aiexploration.perplexity.controller;

import com.aiexploration.perplexity.model.PerplexityResponse;
import com.aiexploration.perplexity.service.AIService;
import com.aiexploration.perplexity.service.HuggingFaceService;
import com.aiexploration.perplexity.service.OpenRouterService;
import com.aiexploration.perplexity.service.PerplexityService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final Map<String, AIService> services;

    public ChatController(
            PerplexityService perplexityService,
            HuggingFaceService huggingFaceService,
            OpenRouterService openRouterService
    ) {
        this.services = new HashMap<>();
        this.services.put("perplexity", perplexityService);
        this.services.put("huggingface", huggingFaceService);
        this.services.put("openrouter", openRouterService);
    }

    @PostMapping
    public ResponseEntity<PerplexityResponse> chat(@RequestBody Map<String, Object> request, HttpSession session) {
        String message = (String) request.get("message");
        String model = (String) request.get("model");
        String format = (String) request.get("format");
        Double temperature = request.get("temperature") != null ? ((Number) request.get("temperature")).doubleValue() : null;
        Integer maxTokens = request.get("maxTokens") != null ? ((Number) request.get("maxTokens")).intValue() : null;
        String systemPromptType = (String) request.get("systemPromptType");
        String customSystemPrompt = (String) request.get("customSystemPrompt");
        String provider = (String) request.get("provider");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Default to perplexity if provider not specified
        if (provider == null || provider.trim().isEmpty()) {
            provider = "perplexity";
        }

        // Get the appropriate service
        AIService service = services.get(provider.toLowerCase());
        if (service == null) {
            log.error("Unknown provider: {}", provider);
            return ResponseEntity.badRequest().build();
        }

        try {
            PerplexityResponse response = service.chat(message, model, format, temperature, maxTokens, systemPromptType, customSystemPrompt, session);

            // Log token usage and finish reason
            if (response != null) {
                String finishReason = null;
                if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                    finishReason = response.getChoices().get(0).getFinishReason();
                }

                if (response.getUsage() != null) {
                    PerplexityResponse.Usage usage = response.getUsage();
                    log.info("Response - Provider: {}, Model: {}, Prompt: {} tokens, Completion: {} tokens, Total: {} tokens, Finish: {}, Execution time: {}ms",
                            provider, model,
                            usage.getPromptTokens(),
                            usage.getCompletionTokens(),
                            usage.getTotalTokens(),
                            finishReason,
                            response.getExecutionTimeMs());
                } else {
                    log.warn("No token usage information available for provider: {}, model: {}, finish reason: {}", provider, model, finishReason);
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
            // Also clear the old key for perplexity compatibility
            if ("perplexity".equalsIgnoreCase(provider)) {
                session.removeAttribute("conversationHistory");
            }
        } else {
            // Clear all history
            session.removeAttribute("conversationHistory");
            session.removeAttribute("conversationHistory_perplexity");
            session.removeAttribute("conversationHistory_huggingface");
            session.removeAttribute("conversationHistory_openrouter");
        }

        return ResponseEntity.ok().build();
    }
}
