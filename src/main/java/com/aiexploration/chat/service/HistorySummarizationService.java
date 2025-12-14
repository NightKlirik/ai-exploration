package com.aiexploration.chat.service;

import com.aiexploration.chat.model.ChatRequest;
import com.aiexploration.chat.model.ChatResponse;
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
        You are a conversation summarizer. Your task is to update and maintain a comprehensive
        summary of an ongoing conversation between a user and an AI assistant.

        The summary should:
        1. Preserve all key information, facts, and decisions discussed
        2. Maintain chronological order of topics
        3. Be clear and easy to understand
        4. Be concise while retaining all important context
        5. When updating an existing summary, integrate new information seamlessly
        6. Avoid redundancy - don't repeat information already in the existing summary

        Provide ONLY the updated summary text, without any additional commentary or prefixes.
        """;

    private static final String UPDATE_SUMMARY_PROMPT = """
        Update the following conversation summary with new messages.

        EXISTING SUMMARY:
        %s

        NEW MESSAGES TO INTEGRATE:
        %s

        Create an updated summary that combines the existing summary with the new information.
        Maintain chronological flow and avoid redundancy.
        """;

    private static final String CREATE_SUMMARY_PROMPT = """
        Create a summary of the following conversation:

        %s

        Focus on key information, facts, and decisions discussed.
        """;

    /**
     * Checks if history needs summarization based on message count
     */
    public boolean needsSummarization(List<ChatRequest.Message> history) {
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
     * Creates or updates a summary of conversation messages
     * If an existing summary is found, it will be updated with new messages
     * Otherwise, a new summary will be created
     */
    public String createSummary(
            List<ChatRequest.Message> history,
            AIService aiService,
            String model,
            Double temperature,
            Integer maxTokens,
            HttpSession tempSession
    ) {
        // Extract conversation messages (excluding system messages and summaries)
        List<ChatRequest.Message> conversationMessages = history.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .filter(m -> !isSummary(m))
                .collect(Collectors.toList());

        int totalCount = conversationMessages.size();
        if (totalCount < SUMMARIZATION_THRESHOLD) {
            return null;
        }

        // Check if there's an existing summary
        ChatRequest.Message existingSummaryMessage = findExistingSummary(history);
        String existingSummary = existingSummaryMessage != null
                ? extractSummaryContent(existingSummaryMessage)
                : null;

        // Get the last N messages to summarize
        List<ChatRequest.Message> messagesToSummarize =
                conversationMessages.subList(totalCount - SUMMARIZATION_THRESHOLD, totalCount);

        // Build conversation text from new messages
        StringBuilder conversationText = new StringBuilder();
        for (ChatRequest.Message msg : messagesToSummarize) {
            conversationText.append(msg.getRole().toUpperCase())
                    .append(": ")
                    .append(msg.getContent())
                    .append("\n\n");
        }

        // Create appropriate prompt based on whether we're updating or creating
        String summarizationRequest;
        if (existingSummary != null && !existingSummary.isEmpty()) {
            // Update existing summary
            summarizationRequest = String.format(
                    UPDATE_SUMMARY_PROMPT,
                    existingSummary,
                    conversationText.toString()
            );
            log.info("Updating existing summary with {} new messages", SUMMARIZATION_THRESHOLD);
        } else {
            // Create new summary
            summarizationRequest = String.format(
                    CREATE_SUMMARY_PROMPT,
                    conversationText.toString()
            );
            log.info("Creating new summary from {} messages", SUMMARIZATION_THRESHOLD);
        }

        // Call AI service to generate/update summary
        try {
            ChatResponse summaryResponse = aiService.chat(
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

            if (summaryResponse != null && summaryResponse.getContent() != null) {
                return summaryResponse.getContent();
            }
        } catch (Exception e) {
            log.error("Failed to generate summary: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Updates existing summary or creates a new one in the history
     * Removes the last N conversation messages that were summarized
     * @return number of messages that were summarized in this operation
     */
    public int applySummary(List<ChatRequest.Message> history, String summary) {
        // Find existing summary message
        ChatRequest.Message existingSummaryMessage = findExistingSummary(history);

        // Separate system messages (excluding summary), conversation messages, and summary
        List<ChatRequest.Message> systemMessages = history.stream()
                .filter(m -> "system".equals(m.getRole()))
                .filter(m -> !isSummary(m))
                .toList();

        List<ChatRequest.Message> conversationMessages = history.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .collect(Collectors.toList());

        int totalCount = conversationMessages.size();
        List<ChatRequest.Message> remainingMessages =
                conversationMessages.subList(0, totalCount - SUMMARIZATION_THRESHOLD);

        // Calculate total messages summarized
        int previouslySummarized = 0;
        if (existingSummaryMessage != null && existingSummaryMessage.getMetadata() != null) {
            Object totalSummarized = existingSummaryMessage.getMetadata().get("totalMessagesSummarized");
            if (totalSummarized instanceof Integer) {
                previouslySummarized = (Integer) totalSummarized;
            }
        }
        int totalSummarized = previouslySummarized + SUMMARIZATION_THRESHOLD;

        // Create or update summary message with metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("isSummary", true);
        metadata.put("messageCount", SUMMARIZATION_THRESHOLD); // Messages summarized in this operation
        metadata.put("totalMessagesSummarized", totalSummarized); // Total messages ever summarized
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("updateCount", existingSummaryMessage != null
                ? ((Integer) existingSummaryMessage.getMetadata().getOrDefault("updateCount", 0)) + 1
                : 1);

        ChatRequest.Message summaryMessage = ChatRequest.Message.builder()
                .role("system")
                .content("Previous conversation summary: " + summary)
                .metadata(metadata)
                .build();

        // Clear and rebuild history with summary at the beginning
        history.clear();
        history.addAll(systemMessages);
        history.add(summaryMessage); // Add summary right after system messages
        history.addAll(remainingMessages);

        if (existingSummaryMessage != null) {
            log.info("Updated existing summary (update #{}, total {} messages summarized)",
                    metadata.get("updateCount"), totalSummarized);
        } else {
            log.info("Created new summary ({} messages summarized)", totalSummarized);
        }

        return SUMMARIZATION_THRESHOLD;
    }

    /**
     * Checks if a message is a summary based on metadata
     */
    private boolean isSummary(ChatRequest.Message message) {
        if (message.getMetadata() == null) {
            return false;
        }
        Object isSummary = message.getMetadata().get("isSummary");
        return Boolean.TRUE.equals(isSummary);
    }

    /**
     * Finds existing summary message in history
     * @return existing summary message or null if not found
     */
    public ChatRequest.Message findExistingSummary(List<ChatRequest.Message> history) {
        if (history == null) {
            return null;
        }

        return history.stream()
                .filter(this::isSummary)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts summary content from summary message
     * @return summary text without the "Previous conversation summary: " prefix
     */
    private String extractSummaryContent(ChatRequest.Message summaryMessage) {
        if (summaryMessage == null || summaryMessage.getContent() == null) {
            return null;
        }

        String content = summaryMessage.getContent();
        String prefix = "Previous conversation summary: ";

        if (content.startsWith(prefix)) {
            return content.substring(prefix.length());
        }

        return content;
    }
}
