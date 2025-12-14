package com.aiexploration.chat.service;

import com.aiexploration.chat.model.ChatResponse;
import jakarta.servlet.http.HttpSession;

public interface AIService {

    ChatResponse chat(
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
