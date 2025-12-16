package com.aiexploration.mcp.weather.model.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonRpcRequest {
    private String jsonrpc;
    private String id;
    private String method;
    private Map<String, Object> params;
}
