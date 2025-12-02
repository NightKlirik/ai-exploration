package com.aiexploration.perplexity.service;

import com.aiexploration.perplexity.config.PerplexityConfig;
import com.aiexploration.perplexity.model.PerplexityRequest;
import com.aiexploration.perplexity.model.PerplexityResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class PerplexityService {

    private static final String RETURN_FORMAT = """
            You are an assistant that must always return responses in a valid API-style JSON format.
            
            Your responses must strictly follow this structure:
            {
              "processing_time_ms": <number>,
              "request": "<user's original request>",
              "message": "<your response to the user>"
            }
            CRITICAL: Your response will FAIL if you add markdown.
            - ❌ WRONG: \\`\\`\\`json{"key":"value"}\\`\\`\\`
            - ✅ CORRECT: {"key":"value"}
            
            CRITICAL RULES:
            1. Your entire response MUST be valid JSON - no text before or after the JSON object
            2. DO NOT WRAP THE JSON IN MARKDOWN CODE BLOCKS(NO ```json OR ```)
            3. The "processing_time_ms" should be a reasonable estimate (e.g., 100-500)
            4. The "request" field should contain a brief summary of what the user asked
            5. The "message" field should contain your actual response to the user's query
            6. Ensure all strings are properly escaped (quotes, newlines, etc.)
            7. Do not include any explanatory text outside the JSON structure
            
            Example response:
            {"processing_time_ms": 250, "request": "explain quantum physics", "message": "Quantum physics is the branch of physics that studies matter and energy at the atomic and subatomic levels..."}
            """;

    private final RestTemplate restTemplate;
    private final PerplexityConfig config;

    public PerplexityService(RestTemplate restTemplate, PerplexityConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public PerplexityResponse chat(String userMessage, String model, String format) {
        String url = config.getApiUrl() + "/chat/completions";

        List<PerplexityRequest.Message> messages = new java.util.ArrayList<>();

        // Add system prompt only if JSON format is requested
        if ("json".equalsIgnoreCase(format)) {
            messages.add(PerplexityRequest.Message.builder()
                    .role("system")
                    .content(RETURN_FORMAT)
                    .build());
        }

        // Add user message
        messages.add(PerplexityRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        PerplexityRequest request = PerplexityRequest.builder()
                .model(model != null ? model : "sonar")
                .messages(messages)
                .maxTokens(1000)
                .temperature(0.2)
                .topP(0.9)
                .stream(false)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<PerplexityRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PerplexityResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                PerplexityResponse.class
        );

        return response.getBody();
    }
}