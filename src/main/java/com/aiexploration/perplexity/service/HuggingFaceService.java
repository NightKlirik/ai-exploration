package com.aiexploration.perplexity.service;

import com.aiexploration.perplexity.config.AIConfig;
import com.aiexploration.perplexity.model.PerplexityRequest;
import com.aiexploration.perplexity.model.PerplexityResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class HuggingFaceService implements AIService {

    private final RestTemplate restTemplate;
    private final AIConfig config;

    public HuggingFaceService(RestTemplate restTemplate, AIConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PerplexityResponse chat(String userMessage, String model, String format, Double temperature, Integer maxTokens, String systemPromptType, String customSystemPrompt, HttpSession session) {
        // HuggingFace Inference API endpoint
        String url = config.getHuggingfaceApiUrl();

        List<PerplexityRequest.Message> messages = new ArrayList<>();

        // Add system prompt if provided
        if ("custom".equalsIgnoreCase(systemPromptType) && customSystemPrompt != null && !customSystemPrompt.trim().isEmpty()) {
            messages.add(PerplexityRequest.Message.builder()
                    .role("system")
                    .content(customSystemPrompt)
                    .build());
        }

        // Add conversation history from session
        String historyKey = "conversationHistory_huggingface";
        List<PerplexityRequest.Message> history = (List<PerplexityRequest.Message>) session.getAttribute(historyKey);
        if (history != null) {
            messages.addAll(history);
        }

        // Add current user message
        messages.add(PerplexityRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        PerplexityRequest request = PerplexityRequest.builder()
                .model(model)
                .messages(messages)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .temperature(temperature != null ? temperature : 0.7)
                .stream(false)
                .parameters(PerplexityRequest.Parameters.builder()
                        .details(true)
                        .build())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getHuggingfaceApiKey());

        HttpEntity<PerplexityRequest> entity = new HttpEntity<>(request, headers);

        // Measure execution time
        long startTime = System.currentTimeMillis();

        ResponseEntity<PerplexityResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                PerplexityResponse.class
        );

        long executionTime = System.currentTimeMillis() - startTime;

        PerplexityResponse responseBody = response.getBody();

        // Add execution time to response
        if (responseBody != null) {
            responseBody.setExecutionTimeMs(executionTime);
        }

        // Save history
        if (responseBody != null) {
            if (history == null) {
                history = new ArrayList<>();
            }

            // Add user message and assistant response to history
            history.add(PerplexityRequest.Message.builder()
                    .role("user")
                    .content(userMessage)
                    .build());

            String assistantMessage = responseBody.getChoices().get(0).getMessage().getContent();
            history.add(PerplexityRequest.Message.builder()
                    .role("assistant")
                    .content(assistantMessage)
                    .build());

            // Save updated history
            session.setAttribute(historyKey, history);
        }

        return responseBody;
    }
}
