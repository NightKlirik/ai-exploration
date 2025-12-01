package com.aiexploration.perplexity.controller;

import com.aiexploration.perplexity.model.PerplexityResponse;
import com.aiexploration.perplexity.service.PerplexityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final PerplexityService perplexityService;

    public ChatController(PerplexityService perplexityService) {
        this.perplexityService = perplexityService;
    }

    @PostMapping
    public ResponseEntity<PerplexityResponse> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String model = request.get("model");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            PerplexityResponse response = perplexityService.chat(message, model);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error on handle message: {}, error: {}", message, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
