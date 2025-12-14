package com.aiexploration.chat.controller;

import com.aiexploration.chat.model.Conversation;
import com.aiexploration.chat.model.Message;
import com.aiexploration.chat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createConversation(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String provider = request.get("provider");
        String model = request.get("model");

        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Conversation conversation = conversationService.createConversation(title, provider, model);

        Map<String, Object> response = new HashMap<>();
        response.put("id", conversation.getId());
        response.put("title", conversation.getTitle());
        response.put("provider", conversation.getProvider());
        response.put("model", conversation.getModel());
        response.put("createdAt", conversation.getCreatedAt());
        response.put("updatedAt", conversation.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllConversations() {
        List<Conversation> conversations = conversationService.getAllConversations();

        List<Map<String, Object>> response = conversations.stream()
                .map(conv -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", conv.getId());
                    map.put("title", conv.getTitle());
                    map.put("provider", conv.getProvider());
                    map.put("model", conv.getModel());
                    map.put("createdAt", conv.getCreatedAt());
                    map.put("updatedAt", conv.getUpdatedAt());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable Long id) {
        try {
            Conversation conversation = conversationService.getConversation(id);
            List<Message> messages = conversationService.getMessages(id);

            Map<String, Object> response = new HashMap<>();
            response.put("id", conversation.getId());
            response.put("title", conversation.getTitle());
            response.put("provider", conversation.getProvider());
            response.put("model", conversation.getModel());
            response.put("createdAt", conversation.getCreatedAt());
            response.put("updatedAt", conversation.getUpdatedAt());

            List<Map<String, Object>> messagesList = messages.stream()
                    .map(msg -> {
                        Map<String, Object> msgMap = new HashMap<>();
                        msgMap.put("id", msg.getId());
                        msgMap.put("role", msg.getRole());
                        msgMap.put("content", msg.getContent());
                        msgMap.put("promptTokens", msg.getPromptTokens());
                        msgMap.put("completionTokens", msg.getCompletionTokens());
                        msgMap.put("totalTokens", msg.getTotalTokens());
                        msgMap.put("executionTimeMs", msg.getExecutionTimeMs());
                        msgMap.put("finishReason", msg.getFinishReason());
                        msgMap.put("isSummary", msg.getIsSummary());
                        msgMap.put("createdAt", msg.getCreatedAt());
                        return msgMap;
                    })
                    .collect(Collectors.toList());

            response.put("messages", messagesList);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error getting conversation: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
        try {
            conversationService.deleteConversation(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deleting conversation: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateConversation(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String title = request.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            Conversation conversation = conversationService.updateConversationTitle(id, title);

            Map<String, Object> response = new HashMap<>();
            response.put("id", conversation.getId());
            response.put("title", conversation.getTitle());
            response.put("provider", conversation.getProvider());
            response.put("model", conversation.getModel());
            response.put("createdAt", conversation.getCreatedAt());
            response.put("updatedAt", conversation.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error updating conversation: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
