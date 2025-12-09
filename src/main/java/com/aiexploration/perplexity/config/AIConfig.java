package com.aiexploration.perplexity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AIConfig {

    @Value("${perplexity.api.url}")
    private String perplexityApiUrl;

    @Value("${perplexity.api.key}")
    private String perplexityApiKey;

    @Value("${huggingface.api.url}")
    private String huggingfaceApiUrl;

    @Value("${huggingface.api.key}")
    private String huggingfaceApiKey;

    @Value("${openrouter.api.url}")
    private String openrouterApiUrl;

    @Value("${openrouter.api.key}")
    private String openrouterApiKey;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getPerplexityApiUrl() {
        return perplexityApiUrl;
    }

    public String getPerplexityApiKey() {
        return perplexityApiKey;
    }

    public String getHuggingfaceApiUrl() {
        return huggingfaceApiUrl;
    }

    public String getHuggingfaceApiKey() {
        return huggingfaceApiKey;
    }

    public String getOpenrouterApiUrl() {
        return openrouterApiUrl;
    }

    public String getOpenrouterApiKey() {
        return openrouterApiKey;
    }
}
