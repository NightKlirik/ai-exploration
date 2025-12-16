package com.aiexploration.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class ChatRequest {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private Map<String, Object> metadata;

        // For tool messages (role="tool")
        private String toolCallId;

        // For assistant messages with tool_calls
        private List<ToolCallInfo> toolCalls;
    }
}
