package com.aiexploration.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
    org.springframework.ai.autoconfigure.mcp.client.SseHttpClientTransportAutoConfiguration.class,
    org.springframework.ai.autoconfigure.mcp.client.McpClientAutoConfiguration.class
})
public class AiExplorationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiExplorationApplication.class, args);
    }
}