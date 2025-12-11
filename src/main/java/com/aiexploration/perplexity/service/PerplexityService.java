package com.aiexploration.perplexity.service;

import com.aiexploration.perplexity.config.AIConfig;
import com.aiexploration.perplexity.model.PerplexityRequest;
import com.aiexploration.perplexity.model.PerplexityResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PerplexityService implements AIService {

    private static final String RETURN_FORMAT = """
            You are an assistant that must always return responses in a valid API-style JSON format.
            
            Your responses must strictly follow this structure:
            {
              "processing_time_ms": <number>,
              "request": "<user's original request>",
              "message": "<your response to the user>"
            }
            CRITICAL: Your response will FAIL if you add markdown.
            - ❌ WRONG: \\`\\`\\`json{"key":"value"}\\`\\`\\`
            - ✅ CORRECT: {"key":"value"}
            
            CRITICAL RULES:
            1. Your entire response MUST be valid JSON - no text before or after the JSON object
            2. DO NOT WRAP THE JSON IN MARKDOWN CODE BLOCKS(NO ```json OR ```)
            3. The "processing_time_ms" should be a reasonable estimate (e.g., 100-500)
            4. The "request" field should contain a brief summary of what the user asked
            5. The "message" field should contain your actual response to the user's query
            6. Ensure all strings are properly escaped (quotes, newlines, etc.)
            7. Do not include any explanatory text outside the JSON structure
            
            Example response:
            {"processing_time_ms": 250, "request": "explain quantum physics", "message": "Quantum physics is the branch of physics that studies matter and energy at the atomic and subatomic levels..."}
            """;
    private static final String TZ = """
            # System Prompt: Походный Консультант
            
            ## Персонаж
            Ты - опытный турист-походник 25-летним стажем. За плечами у тебя сотни походов: от простых выходных на природе до серьёзных горных экспедиций.
            
            ## Твоя задача
            Через дружеский диалог собрать всю необходимую информацию о предстоящем походе и составить персонализированный список снаряжения.
            
            ### Запрещено:
            ⛔ Задавать больше ОДНОГО вопроса в сообщении
            ⛔ Давать советы, пока не собрана вся информация
            ⛔ Рассказывать истории и байки до финала
            ⛔ Отвлекаться от сбора информации
            ⛔ Писать длинные сообщения — только вопрос и ничего лишнего
            
            ### Обязательно:
            ✅ Каждое сообщение = ОДИН короткий вопрос
            ✅ Никаких советов до финального списка
            ✅ Строго следуй порядку вопросов
            ✅ Максимум 5 вопросов
            
            ## Ключевые темы (выбери самые важные для конкретного случая)
            - Тип и продолжительность похода
            - Сезон
            - Количество участников
            - Ночёвка: палатка, домик или без ночёвки
            - Питание: костёр, горелка, сухпаёк
            
            ## Формат результата
            После получения ответов (или когда информации достаточно) выведи:
            
            ### 🎒 СПИСОК СНАРЯЖЕНИЯ ДЛЯ ПОХОДА
            
            **Обязательное:**
            [список]
            
            **Одежда:**
            [с учётом погоды]
            
            **Еда и вода:**
            [рекомендации]
            
            **Аптечка:**
            [базовый набор]

            ## Начало
            Поприветствуй, скажи, что поможешь подобрать снаряжение для похода. И задай первый вопрос — куда и на сколько собираются.
            """;

    private final RestTemplate restTemplate;
    private final AIConfig config;
    private final HistorySummarizationService summarizationService;

    public PerplexityService(RestTemplate restTemplate, AIConfig config, HistorySummarizationService summarizationService) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.summarizationService = summarizationService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PerplexityResponse chat(String userMessage, String model, String format, Double temperature, Integer maxTokens, String systemPromptType, String customSystemPrompt, HttpSession session, Boolean autoSummarize) {
        String url = config.getPerplexityApiUrl() + "/chat/completions";

        List<PerplexityRequest.Message> messages = new ArrayList<>();

        // Determine if we need to use history (for TZ prompt or custom prompt)
        boolean useTZPrompt = "tz".equalsIgnoreCase(systemPromptType);
        boolean useCustomPrompt = "custom".equalsIgnoreCase(systemPromptType) && customSystemPrompt != null && !customSystemPrompt.trim().isEmpty();

        // Add system prompt based on type
        if (useCustomPrompt) {
            messages.add(PerplexityRequest.Message.builder()
                    .role("system")
                    .content(customSystemPrompt)
                    .build());
        } else if (useTZPrompt) {
            messages.add(PerplexityRequest.Message.builder()
                    .role("system")
                    .content(TZ)
                    .build());
        } else if ("json".equalsIgnoreCase(format)) {
            messages.add(PerplexityRequest.Message.builder()
                    .role("system")
                    .content(RETURN_FORMAT)
                    .build());
        }

        // Add conversation history from session if using TZ prompt or custom prompt
        if (useTZPrompt || useCustomPrompt) {
            List<PerplexityRequest.Message> history = (List<PerplexityRequest.Message>) session.getAttribute("conversationHistory");
            if (history != null) {
                messages.addAll(history);
            }
        }

        // Add current user message
        messages.add(PerplexityRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        PerplexityRequest request = PerplexityRequest.builder()
                .model(model != null ? model : "sonar")
                .messages(messages)
                .maxTokens(maxTokens != null ? maxTokens : 2000)
                .temperature(temperature != null ? temperature : 0.2)
                .topP(0.9)
                .stream(false)
                .parameters(PerplexityRequest.Parameters.builder()
                        .details(true)
                        .build())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getPerplexityApiKey());

        HttpEntity<PerplexityRequest> entity = new HttpEntity<>(request, headers);

        // Measure execution time
        long startTime = System.currentTimeMillis();

        ResponseEntity<PerplexityResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                PerplexityResponse.class
        );

        long executionTime = System.currentTimeMillis() - startTime;

        PerplexityResponse responseBody = response.getBody();

        // Add execution time to response
        if (responseBody != null) {
            responseBody.setExecutionTimeMs(executionTime);
        }

        // Save history for TZ prompt or custom prompt
        if ((useTZPrompt || useCustomPrompt) && responseBody != null) {
            List<PerplexityRequest.Message> history = (List<PerplexityRequest.Message>) session.getAttribute("conversationHistory");
            if (history == null) {
                history = new ArrayList<>();
            }

            // Add user message and assistant response to history
            history.add(PerplexityRequest.Message.builder()
                    .role("user")
                    .content(userMessage)
                    .build());

            String assistantMessage = responseBody.getChoices().get(0).getMessage().getContent();
            history.add(PerplexityRequest.Message.builder()
                    .role("assistant")
                    .content(assistantMessage)
                    .build());

            // Check if response contains completion marker (only for TZ prompt)
            if (useTZPrompt && assistantMessage.contains("СПИСОК СНАРЯЖЕНИЯ ДЛЯ ПОХОДА")) {
                // Clear history - conversation is complete
                session.removeAttribute("conversationHistory");
            } else {
                // Save updated history
                session.setAttribute("conversationHistory", history);

                // Auto-summarization logic
                PerplexityResponse.SummarizationInfo summarizationInfo = null;
                if (Boolean.TRUE.equals(autoSummarize) &&
                        summarizationService.needsSummarization(history)) {

                    log.info("Triggering auto-summarization for Perplexity");

                    // Create temporary session to avoid history pollution
                    HttpSession tempSession = new org.springframework.mock.web.MockHttpSession();

                    String summary = summarizationService.createSummary(
                            history, this, model, temperature, maxTokens, tempSession
                    );

                    if (summary != null) {
                        summarizationService.applySummary(history, summary);
                        session.setAttribute("conversationHistory", history);

                        summarizationInfo = PerplexityResponse.SummarizationInfo.builder()
                                .summarizationOccurred(true)
                                .messagesSummarized(10)
                                .summaryContent(summary)
                                .build();

                        log.info("Summarization completed successfully");
                    } else {
                        log.warn("Summarization failed, keeping original messages");
                    }
                }

                // Set summarization info in response
                if (responseBody != null && summarizationInfo != null) {
                    responseBody.setSummarizationInfo(summarizationInfo);
                }
            }
        }

        return responseBody;
    }
}