package com.questionbank.QuestionBank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.questionbank.QuestionBank.dto.AiCorrectionDTO;
import com.questionbank.QuestionBank.dto.AnswerDTO;
import com.questionbank.QuestionBank.dto.QuestionDTO;
import com.questionbank.QuestionBank.entity.Answer;
import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.entity.QuestionType;
import com.questionbank.QuestionBank.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Service for AI-based answer evaluation using Mistral API
@Service
public class AiCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(AiCorrectionService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AnswerService answerService;
    private final QuestionService questionService;

    @Value("${ai.mistral.api.key}")
    private String mistralApiKey;

    @Value("${ai.mistral.api.url:https://api.mistral.ai/v1/chat/completions}")
    private String mistralApiUrl;

    @Value("${ai.correction.threshold:0.7}")
    private double correctionThreshold;

    @Value("${ai.correction.max-tokens:1000}")
    private int maxTokens;

    @Autowired
    public AiCorrectionService(ObjectMapper objectMapper, AnswerService answerService, QuestionService questionService) {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.answerService = answerService;
        this.questionService = questionService;
    }

    public CompletableFuture<AiCorrectionDTO> correctAnswerById(UUID answerId) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            try {
                AnswerDTO answerResponse = answerService.getAnswerById(answerId);
                QuestionDTO questionResponse = questionService.getQuestionById(answerResponse.getQuestionId());

                Question question = convertToQuestionEntity(questionResponse);
                Answer answer = convertToAnswerEntity(answerResponse);
                AiCorrectionResult result = correctAnswer(question, answer).get();
                AiCorrectionDTO response = convertToResponseDTO(result, startTime);

                return response;

            } catch (Exception e) {
                log.error("Error during AI correction for answer ID {}: {}",
                           answerId, e.getMessage(), e);

                AiCorrectionDTO errorResponse = new AiCorrectionDTO(
                    answerId,
                    "AI correction failed: " + e.getMessage()
                );
                errorResponse.setProcessingTimeMs(System.currentTimeMillis() - startTime);

                return errorResponse;
            }
        });
    }

    public CompletableFuture<AiCorrectionDTO[]> batchCorrectAnswers(UUID[] answerIds) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            try {
                AiCorrectionDTO[] responses = new AiCorrectionDTO[answerIds.length];

                for (int i = 0; i < answerIds.length; i++) {
                    try {
                        AnswerDTO answerResponse = answerService.getAnswerById(answerIds[i]);
                        QuestionDTO questionResponse = questionService.getQuestionById(answerResponse.getQuestionId());
                        Question question = convertToQuestionEntity(questionResponse);
                        Answer answer = convertToAnswerEntity(answerResponse);
                        AiCorrectionResult result = correctAnswer(question, answer).get();
                        responses[i] = convertToResponseDTO(result, startTime);
                    } catch (Exception e) {
                        log.error("Error during AI correction for answer ID {}: {}",
                                   answerIds[i], e.getMessage(), e);

                        responses[i] = new AiCorrectionDTO(
                            answerIds[i],
                            "AI correction failed: " + e.getMessage()
                        );
                        responses[i].setProcessingTimeMs(System.currentTimeMillis() - startTime);
                    }
                }

                return responses;

            } catch (Exception e) {
                log.error("Error during batch AI correction: {}", e.getMessage(), e);
                throw new RuntimeException("Batch AI correction failed", e);
            }
        });
    }

    private Question convertToQuestionEntity(QuestionDTO questionResponse) {
        Question question = new Question(
            questionResponse.getTitle(),
            questionResponse.getType(),
            questionResponse.getModule(),
            questionResponse.getUnit(),
            questionResponse.getContent(),
            questionResponse.getMediaFilePaths(),
            questionResponse.getAllowMediaInsertion(),
            questionResponse.getAllowedMediaTypes(),
            questionResponse.getDifficultyLevel(),
            questionResponse.getPoints(),
            questionResponse.getTimeLimitMinutes(),
            questionResponse.getConfigurationData(),
            questionResponse.isActive(),
            questionResponse.getCreatedBy(),
            questionResponse.getUpdatedBy()
        );
        question.setId(questionResponse.getId());
        return question;
    }

    private Answer convertToAnswerEntity(AnswerDTO answerResponse) {
        Answer answer = new Answer(
            answerResponse.getQuestionId(),
            answerResponse.getType(),
            answerResponse.getContent(),
            answerResponse.getMediaFilePaths(),
            answerResponse.getLanguage(),
            answerResponse.isCorrect(),
            answerResponse.getScore(),
            answerResponse.getMaxScore(),
            answerResponse.getFeedback(),
            answerResponse.getSubmittedBy(),
            answerResponse.isActive(),
            answerResponse.getCreatedAt(),
            answerResponse.getUpdatedAt()
        );
        answer.setId(answerResponse.getId());
        return answer;
    }

    private AiCorrectionDTO convertToResponseDTO(AiCorrectionResult result, long startTime) {
        AiCorrectionDTO response = new AiCorrectionDTO(
            UUID.fromString(result.getAnswerId()),
            result.getScore(),
            result.getMaxScore(),
            result.isCorrect(),
            result.getFeedback()
        );

        response.setCorrectionMethod(result.getCorrectionMethod());
        response.setStrengths(java.util.Arrays.asList(result.getStrengths()));
        response.setWeaknesses(java.util.Arrays.asList(result.getWeaknesses()));
        response.setProcessingTimeMs(System.currentTimeMillis() - startTime);
        response.setAiModel("mistral-medium-latest");
        response.setStatus("SUCCESS");

        return response;
    }

    public CompletableFuture<AiCorrectionResult> correctAnswer(Question question, Answer answer) {

        if (!isQuestionTypeSupported(question.getType())) {
            log.info("Question type {} not supported for AI correction, using preset correction", question.getType());
            return CompletableFuture.completedFuture(createFallbackResult(answer));
        }

        try {
            String prompt = buildPrompt(question, answer);

            return callMistralApi(prompt)
                    .map(response -> {
                        try {
                            log.debug("Raw AI response: {}", response);
                            return parseAiCorrectionResponse(response, answer, question);
                        } catch (Exception e) {
                            log.error("Error parsing AI response for {}: {}", question.getType(), e.getMessage());
                            return createFallbackResult(answer);
                        }
                    })
                    .toFuture();
        } catch (Exception e) {
            String answerId = answer.getId() != null ? answer.getId().toString() : "pending";
            log.error("Error during AI correction for answer {}: {}", answerId, e.getMessage(), e);
            return CompletableFuture.completedFuture(createFallbackResult(answer));
        }
    }

    private boolean isQuestionTypeSupported(QuestionType questionType) {
        switch (questionType) {
            case ESSAY_SHORT:
            case ESSAY_LONG:
            case CODING:
                return true;
            default:
                return false;
        }
    }

    private Mono<String> callMistralApi(String prompt) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "mistral-medium-latest");
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            requestBody.set("messages", objectMapper.createArrayNode().add(message));
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", 0.1);
            requestBody.put("top_p", 0.9);
            requestBody.put("stream", false);

            return webClient.post()
                    .uri(mistralApiUrl)
                    .header("Authorization", "Bearer " + mistralApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(error -> {
                        if (error instanceof WebClientResponseException) {
                            WebClientResponseException wcre = (WebClientResponseException) error;
                            log.error("Mistral API error: {} - {}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
                        } else {
                            log.error("Error calling Mistral API: {}", error.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Error preparing Mistral API request: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    // Build AI prompt with question context and answer content
    private String buildPrompt(Question question, Answer answer) {
        String role = getRoleForQuestionType(question.getType());
        String considerations = getConsiderationsForQuestionType(question.getType());
        String contentLabel = getContentLabelForQuestionType(question.getType());
        String additionalInfo = getAdditionalInfoForQuestionType(question, answer);

        String promptTemplate = """
            You are a high school %s evaluating a %s.

            Question: %s
            Question Content: %s
            %s: %s
            %s
            Max Score: %s

            Evaluate the answer and respond with a JSON object:
            {
                "score": <score out of %s>,
                "maxScore": %s,
                "isCorrect": <true if score >= 70%% of max score>,
                "feedback": "<constructive feedback>",
                "strengths": ["<strength 1>", "<strength 2>"],
                "weaknesses": ["<area for improvement 1>", "<area for improvement 2>"]
            }

            Consider: %s. Provide encouraging feedback suitable for high school students.
            """;

        // Add coding-specific instructions if compilation results available
        if (question.getType() == QuestionType.CODING && additionalInfo.contains("Compilation Results:")) {
            promptTemplate += """

                IMPORTANT: This is a coding question with compilation results from Judge0 API.
                When evaluating, pay special attention to:
                1. Whether the code compiled and executed successfully
                2. The actual output vs expected output (check the Output field in compilation results)
                3. Code quality and best practices
                4. Execution time and memory usage efficiency
                5. Any error messages or compilation issues
                6. Use the compilation results to provide more accurate scoring

                The compilation results include:
                - Compilation status and method used
                - Actual program output
                - Execution time and memory usage
                - Any error messages
                - Test case results
                """;
        }

        return String.format(promptTemplate,
            role,
            question.getType().toString().toLowerCase().replace("_", " "),
            question.getTitle(),
            question.getContent(),
            contentLabel,
            answer.getContent(),
            additionalInfo,
            question.getPoints(),
            question.getPoints(),
            question.getPoints(),
            considerations
        );
    }

    private String getRoleForQuestionType(QuestionType questionType) {
        switch (questionType) {
            case CODING:
                return "programming teacher";
            default:
                return "teacher";
        }
    }

    private String getContentLabelForQuestionType(QuestionType questionType) {
        switch (questionType) {
            case CODING:
                return "Student Code";
            default:
                return "Student Answer";
        }
    }

    private String getAdditionalInfoForQuestionType(Question question, Answer answer) {
        switch (question.getType()) {
            case CODING:
                StringBuilder info = new StringBuilder();
                info.append(String.format("Language: %s", answer.getLanguage()));

                if (answer.getFeedback() != null && !answer.getFeedback().isEmpty()) {
                    if (answer.getFeedback().contains("Code Compilation Results:")) {
                        info.append("\n\nCompilation Results: ").append(answer.getFeedback());
                    } else if (answer.getFeedback().contains("Code compilation failed:")) {
                        info.append("\n\nCompilation Status: ").append(answer.getFeedback());
                    }
                }

                return info.toString();
            default:
                return "";
        }
    }

    private String getConsiderationsForQuestionType(QuestionType questionType) {
        switch (questionType) {
            case ESSAY_SHORT:
                return "accuracy, completeness, clarity, and relevance";
            case ESSAY_LONG:
                return "depth of analysis, argument structure, evidence usage, writing quality, and comprehensive coverage";
            case CODING:
                return "code correctness, efficiency, readability, best practices, adherence to requirements, and compilation results from Judge0 API";
            default:
                return "accuracy, completeness, clarity, and relevance";
        }
    }

    // Parse AI response and extract scoring information
    private AiCorrectionResult parseAiCorrectionResponse(String aiResponse, Answer answer, Question question) {
        try {
            String cleanedResponse = cleanAiResponse(aiResponse);
            log.debug("Cleaned AI response: {}", cleanedResponse);

            JsonNode responseNode = objectMapper.readTree(cleanedResponse);
            log.debug("Parsed response node keys: {}", responseNode.fieldNames());

            if (responseNode.has("score") || responseNode.has("feedback")) {
                AiCorrectionResult result = new AiCorrectionResult();
                result.setAnswerId(answer.getId() != null ? answer.getId().toString() : "pending");
                result.setScore(responseNode.path("score").asDouble(0.0));
                result.setMaxScore(responseNode.path("maxScore").asDouble(question.getPoints()));
                result.setCorrect(responseNode.path("isCorrect").asBoolean(false));
                result.setFeedback(responseNode.path("feedback").asText(""));
                result.setCorrectionMethod("AI");

                if (responseNode.has("strengths")) {
                    result.setStrengths(parseStringArray(responseNode.get("strengths")));
                }
                if (responseNode.has("weaknesses")) {
                    result.setWeaknesses(parseStringArray(responseNode.get("weaknesses")));
                }

                log.debug("Successfully parsed AI response as direct JSON object");
                return result;
            }

            JsonNode choices = responseNode.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                String content = message.path("content").asText("");

                JsonNode contentNode = objectMapper.readTree(content);

                AiCorrectionResult result = new AiCorrectionResult();
                result.setAnswerId(answer.getId() != null ? answer.getId().toString() : "pending");
                result.setScore(contentNode.path("score").asDouble(0.0));
                result.setMaxScore(contentNode.path("maxScore").asDouble(question.getPoints()));
                result.setCorrect(contentNode.path("isCorrect").asBoolean(false));
                result.setFeedback(contentNode.path("feedback").asText(""));
                result.setCorrectionMethod("AI");

                if (contentNode.has("strengths")) {
                    result.setStrengths(parseStringArray(contentNode.get("strengths")));
                }
                if (contentNode.has("weaknesses")) {
                    result.setWeaknesses(parseStringArray(contentNode.get("weaknesses")));
                }

                return result;
            } else {
                log.warn("No valid choices found in AI response. Response structure: {}", responseNode.toPrettyString());
                return createFallbackResult(answer);
            }
        } catch (Exception e) {
            log.error("Error parsing AI response for {}: {}", question.getType(), e.getMessage());
            log.debug("Raw AI response that failed to parse: {}", aiResponse);
            return createFallbackResult(answer);
        }
    }

    // Remove markdown formatting and extract JSON object from AI response
    private String cleanAiResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return Utils.Constants.EMPTY_JSON_OBJECT;
        }

        String cleaned = aiResponse.trim();

        cleaned = cleaned.replaceAll("```json\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*", "");

        cleaned = cleaned.replaceAll("^`+", "");
        cleaned = cleaned.replaceAll("`+$", "");

        int startBrace = cleaned.indexOf('{');
        int endBrace = cleaned.lastIndexOf('}');

        if (startBrace >= 0 && endBrace > startBrace) {
            cleaned = cleaned.substring(startBrace, endBrace + 1);
        }

        if (!cleaned.trim().startsWith("{")) {
            log.warn("No valid JSON structure found in AI response, returning empty object");
            return Utils.Constants.EMPTY_JSON_OBJECT;
        }

        return cleaned;
    }

    private String[] parseStringArray(JsonNode node) {
        if (node.isArray()) {
            String[] result = new String[node.size()];
            for (int i = 0; i < node.size(); i++) {
                result[i] = node.get(i).asText("");
            }
            return result;
        }
        return new String[0];
    }

    private AiCorrectionResult createFallbackResult(Answer answer) {
        AiCorrectionResult result = new AiCorrectionResult();
        result.setAnswerId(answer.getId() != null ? answer.getId().toString() : "pending");
        result.setScore(0.0);
        result.setMaxScore(10.0);
        result.setCorrect(false);
        result.setFeedback("AI correction unavailable. Please review manually.");
        result.setCorrectionMethod("FALLBACK");
        return result;
    }

    public static class AiCorrectionResult {
        private String answerId;
        private double score;
        private double maxScore;
        private boolean correct;
        private String feedback;
        private String correctionMethod;
        private String[] strengths;
        private String[] weaknesses;

        public String getAnswerId() { return answerId; }
        public void setAnswerId(String answerId) { this.answerId = answerId; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }

        public double getMaxScore() { return maxScore; }
        public void setMaxScore(double maxScore) { this.maxScore = maxScore; }

        public boolean isCorrect() { return correct; }
        public void setCorrect(boolean correct) { this.correct = correct; }

        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }

        public String getCorrectionMethod() { return correctionMethod; }
        public void setCorrectionMethod(String correctionMethod) { this.correctionMethod = correctionMethod; }

        public String[] getStrengths() { return strengths; }
        public void setStrengths(String[] strengths) { this.strengths = strengths; }

        public String[] getWeaknesses() { return weaknesses; }
        public void setWeaknesses(String[] weaknesses) { this.weaknesses = weaknesses; }
    }
}
