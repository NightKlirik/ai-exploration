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
public class McpToolExecutionRequest {
    private String serverId;
    private String toolName;
    private Map<String, Object> arguments;
}
