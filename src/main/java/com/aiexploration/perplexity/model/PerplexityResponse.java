package com.aiexploration.perplexity.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PerplexityResponse {

    private String id;

    private String model;

    private String object;

    private Long created;

    private List<Choice> choices;

    private Usage usage;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @Data
    public static class Choice {
        private Integer index;
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}