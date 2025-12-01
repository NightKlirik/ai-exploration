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

    private final RestTemplate restTemplate;
    private final PerplexityConfig config;

    public PerplexityService(RestTemplate restTemplate, PerplexityConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public PerplexityResponse chat(String userMessage, String model) {
        String url = config.getApiUrl() + "/chat/completions";

        PerplexityRequest request = PerplexityRequest.builder()
                .model(model != null ? model : "llama-3.1-sonar-small-128k-online")
                .messages(List.of(
                        PerplexityRequest.Message.builder()
                                .role("user")
                                .content(userMessage)
                                .build()
                ))
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