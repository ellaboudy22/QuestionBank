package com.questionbank.QuestionBank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.questionbank.QuestionBank.entity.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Data transfer object for AI correction requests and responses
@Schema(description = "Unified DTO for AI-based answer correction")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiCorrectionDTO {

    @NotNull(message = "Answer ID is required")
    @Schema(description = "ID of the answer to be corrected", required = true)
    private UUID answerId;

    @NotNull(message = "Question type is required")
    @Schema(description = "Type of question for appropriate AI processing", required = true)
    private QuestionType questionType;

    @Size(min = 1, max = 10000, message = "Answer content must be between 1 and 10000 characters")
    @Schema(description = "Content of the answer to be evaluated", required = true)
    private String answerContent;

    @Schema(description = "Programming language for code answers")
    private String language;

    @Schema(description = "Expected or reference answer for comparison")
    private String expectedAnswer;

    @Schema(description = "Additional context for AI evaluation")
    private String context;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Score assigned by AI")
    private Double score;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Maximum possible score")
    private Double maxScore;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Whether the answer is considered correct")
    private Boolean isCorrect;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "AI-generated feedback")
    private String feedback;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Method used for correction")
    private String correctionMethod;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "AI confidence level (0-1)")
    private Double confidence;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "AI model used for correction")
    private String aiModel;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Processing status")
    private String status;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Identified strengths in the answer")
    private List<String> strengths;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Identified weaknesses in the answer")
    private List<String> weaknesses;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Processing time in milliseconds")
    private Long processingTimeMs;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Error message if correction failed")
    private String errorMessage;

    public AiCorrectionDTO() {}

    public AiCorrectionDTO(UUID answerId, QuestionType questionType, String answerContent) {
        this.answerId = answerId;
        this.questionType = questionType;
        this.answerContent = answerContent;
    }

    public AiCorrectionDTO(UUID answerId, Double score, Double maxScore, Boolean isCorrect, String feedback) {
        this.answerId = answerId;
        this.score = score;
        this.maxScore = maxScore;
        this.isCorrect = isCorrect;
        this.feedback = feedback;
        this.status = "SUCCESS";
    }

    public AiCorrectionDTO(UUID answerId, String errorMessage) {
        this.answerId = answerId;
        this.errorMessage = errorMessage;
        this.status = "ERROR";
        this.score = 0.0;
        this.maxScore = 100.0;
        this.isCorrect = false;
        this.feedback = "AI correction failed: " + errorMessage;
    }

    public UUID getAnswerId() {
        return answerId;
    }

    public void setAnswerId(UUID answerId) {
        this.answerId = answerId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getAnswerContent() {
        return answerContent;
    }

    public void setAnswerContent(String answerContent) {
        this.answerContent = answerContent;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }

    public Boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(Boolean correct) {
        this.isCorrect = correct;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getCorrectionMethod() {
        return correctionMethod;
    }

    public void setCorrectionMethod(String correctionMethod) {
        this.correctionMethod = correctionMethod;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getWeaknesses() {
        return weaknesses;
    }

    public void setWeaknesses(List<String> weaknesses) {
        this.weaknesses = weaknesses;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "AiCorrectionDTO{" +
                "answerId=" + answerId +
                ", questionType=" + questionType +
                ", answerContent='" + answerContent + '\'' +
                ", language='" + language + '\'' +
                ", score=" + score +
                ", maxScore=" + maxScore +
                ", isCorrect=" + isCorrect +
                ", feedback='" + feedback + '\'' +
                ", correctionMethod='" + correctionMethod + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
