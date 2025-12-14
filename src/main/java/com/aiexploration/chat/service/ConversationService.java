package com.aiexploration.chat.service;

import com.aiexploration.chat.model.Conversation;
import com.aiexploration.chat.model.Message;
import com.aiexploration.chat.repository.ConversationRepository;
import com.aiexploration.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public Conversation createConversation(String title, String provider, String model) {
        Conversation conversation = new Conversation();
        conversation.setTitle(title);
        conversation.setProvider(provider);
        conversation.setModel(model);

        Conversation saved = conversationRepository.save(conversation);
        log.info("Created new conversation: id={}, title={}, provider={}", saved.getId(), title, provider);
        return saved;
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc();
    }

    public Conversation getConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + id));
    }

    @Transactional
    public Message addMessage(Long conversationId, String role, String content,
                             Integer promptTokens, Integer completionTokens, Integer totalTokens,
                             Long executionTimeMs, String finishReason, Boolean isSummary) {
        Conversation conversation = getConversation(conversationId);

        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        message.setExecutionTimeMs(executionTimeMs);
        message.setFinishReason(finishReason);
        message.setIsSummary(isSummary != null ? isSummary : false);

        Message saved = messageRepository.save(message);
        log.info("Added message to conversation {}: role={}, tokens={}", conversationId, role, totalTokens);
        return saved;
    }

    public List<Message> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public void deleteConversation(Long id) {
        conversationRepository.deleteById(id);
        log.info("Deleted conversation: id={}", id);
    }

    @Transactional
    public Conversation updateConversationTitle(Long id, String title) {
        Conversation conversation = getConversation(id);
        conversation.setTitle(title);
        return conversationRepository.save(conversation);
    }
}
