package com.aiexploration.chat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallInfo {

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    @JsonProperty("result")
    private Object result;

    @JsonProperty("execution_time_ms")
    private Long executionTimeMs;

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("error")
    private String error;
}
