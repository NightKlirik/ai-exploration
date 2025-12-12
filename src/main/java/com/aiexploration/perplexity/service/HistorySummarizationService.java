package com.aiexploration.perplexity.service;

import com.aiexploration.perplexity.model.PerplexityRequest;
import com.aiexploration.perplexity.model.PerplexityResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HistorySummarizationService {

    private static final int SUMMARIZATION_THRESHOLD = 4;
    private static final String SUMMARIZATION_SYSTEM_PROMPT = """
        You are a conversation summarizer. Your task is to create a concise summary
        of the following conversation between a user and an AI assistant.

        The summary should:
        1. Preserve all key information, facts, and decisions discussed
        2. Maintain chronological order of topics
        3. Be clear and easy to understand
        4. Be approximately 30-40% of the original length
        5. Start with "SUMMARY: " prefix

        Provide ONLY the summary text, without any additional commentary.
        """;

    /**
     * Checks if history needs summarization based on message count
     */
    public boolean needsSummarization(List<PerplexityRequest.Message> history) {
        if (history == null) {
            return false;
        }

        long conversationMessageCount = history.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .filter(m -> !isSummary(m))  // Exclude summaries from count
                .count();

        return conversationMessageCount >= SUMMARIZATION_THRESHOLD;
    }

    /**
     * Creates a summary of the last 10 conversation messages
     */
    public String createSummary(
            List<PerplexityRequest.Message> history,
            AIService aiService,
            String model,
            Double temperature,
            Integer maxTokens,
            HttpSession tempSession
    ) {
        // Extract last 10 conversation messages (excluding system messages)
        List<PerplexityRequest.Message> conversationMessages = history.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .collect(Collectors.toList());

        int totalCount = conversationMessages.size();
        if (totalCount < SUMMARIZATION_THRESHOLD) {
            return null;
        }

        // Get the last 10 messages to summarize
        List<PerplexityRequest.Message> messagesToSummarize =
                conversationMessages.subList(totalCount - SUMMARIZATION_THRESHOLD, totalCount);

        // Build summarization prompt
        StringBuilder conversationText = new StringBuilder();
        for (PerplexityRequest.Message msg : messagesToSummarize) {
            conversationText.append(msg.getRole().toUpperCase())
                    .append(": ")
                    .append(msg.getContent())
                    .append("\n\n");
        }

        String summarizationRequest = "Please summarize this conversation:\n\n" + conversationText;

        // Call AI service to generate summary
        try {
            PerplexityResponse summaryResponse = aiService.chat(
                    summarizationRequest,
                    model,
                    "text", // Always use text format for summaries
                    temperature != null ? temperature : 0.3, // Lower temperature for consistency
                    maxTokens != null ? maxTokens / 2 : 1000, // Use less tokens for summary
                    "custom",
                    SUMMARIZATION_SYSTEM_PROMPT,
                    tempSession,
                    false // Don't trigger summarization for summary generation
            );

            if (summaryResponse != null &&
                    summaryResponse.getChoices() != null &&
                    !summaryResponse.getChoices().isEmpty()) {
                return summaryResponse.getChoices().get(0).getMessage().getContent();
            }
        } catch (Exception e) {
            log.error("Failed to generate summary: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Replaces the last messages with a summary in the history
     * @return number of messages that were summarized
     */
    public int applySummary(List<PerplexityRequest.Message> history, String summary) {
        // Remove last conversation messages
        List<PerplexityRequest.Message> systemMessages = history.stream()
                .filter(m -> "system".equals(m.getRole()))
                .collect(Collectors.toList());

        List<PerplexityRequest.Message> conversationMessages = history.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .filter(m -> !isSummary(m))  // Exclude old summaries
                .collect(Collectors.toList());

        int totalCount = conversationMessages.size();
        List<PerplexityRequest.Message> remainingMessages =
                conversationMessages.subList(0, totalCount - SUMMARIZATION_THRESHOLD);

        // Clear and rebuild history
        history.clear();
        history.addAll(systemMessages);
        history.addAll(remainingMessages);

        // Add summary message with metadata
        // Use "system" role to avoid breaking user/assistant alternation
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isSummary", true);
        metadata.put("messageCount", SUMMARIZATION_THRESHOLD);
        metadata.put("timestamp", System.currentTimeMillis());

        history.add(PerplexityRequest.Message.builder()
                .role("system")
                .content("Previous conversation summary: " + summary)
                .metadata(metadata)
                .build());

        return SUMMARIZATION_THRESHOLD;
    }

    /**
     * Checks if a message is a summary based on metadata
     */
    private boolean isSummary(PerplexityRequest.Message message) {
        if (message.getMetadata() == null) {
            return false;
        }
        Object isSummary = message.getMetadata().get("isSummary");
        return Boolean.TRUE.equals(isSummary);
    }
}
