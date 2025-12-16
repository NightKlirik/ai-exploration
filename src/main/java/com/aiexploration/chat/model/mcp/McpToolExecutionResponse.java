package com.aiexploration.chat.model.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolExecutionResponse {
    private boolean success;
    private Object content;
    private String error;
    private Long executionTimeMs;
    private Map<String, Object> metadata;
}
