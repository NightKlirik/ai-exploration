package com.aiexploration.chat.config;

import io.github.sashirestela.openai.SimpleOpenAI;
import io.github.sashirestela.openai.SimpleOpenAIDeepseek;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIClientConfig {

    @Value("${openai.api-key}")
    private String openaiApiKey;

    @Value("${openai.base-url:https://api.openai.com/v1/}")
    private String openaiBaseUrl;

    @Value("${deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Bean(name = "openAiClient")
    public SimpleOpenAI openAiClient() {
        return SimpleOpenAI.builder()
                .apiKey(openaiApiKey)
                .build();
    }

    @Bean(name = "deepSeekClient")
    public SimpleOpenAIDeepseek deepSeekClient() {
        return SimpleOpenAIDeepseek.builder()
                .apiKey(deepseekApiKey)
                .baseUrl(deepseekBaseUrl)
                .build();
    }
}
