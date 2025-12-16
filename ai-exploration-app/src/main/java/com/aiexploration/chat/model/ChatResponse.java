package com.aiexploration.chat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String content;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    private Usage usage;

    @JsonProperty("finish_reason")
    private String finishReason;

    @JsonProperty("summarization_info")
    private SummarizationInfo summarizationInfo;

    @JsonProperty("tool_calls")
    private List<ToolCallInfo> toolCalls;

    @JsonProperty("had_tool_calls")
    private Boolean hadToolCalls;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummarizationInfo {
        @JsonProperty("summarization_occurred")
        private Boolean summarizationOccurred;

        @JsonProperty("messages_summarized")
        private Integer messagesSummarized;

        @JsonProperty("summary_content")
        private String summaryContent;
    }
}
