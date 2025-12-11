package com.aiexploration.perplexity.service;

import com.aiexploration.perplexity.model.PerplexityResponse;
import jakarta.servlet.http.HttpSession;

public interface AIService {

    PerplexityResponse chat(
            String userMessage,
            String model,
            String format,
            Double temperature,
            Integer maxTokens,
            String systemPromptType,
            String customSystemPrompt,
            HttpSession session,
            Boolean autoSummarize
    );
}
