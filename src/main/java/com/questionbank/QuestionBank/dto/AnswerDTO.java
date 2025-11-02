package com.questionbank.QuestionBank.dto;

import com.questionbank.QuestionBank.entity.Answer;
import com.questionbank.QuestionBank.entity.AnswerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Data transfer object for answer requests and responses
@Schema(description = "Unified DTO for answer operations (create, update, response)")
public class AnswerDTO {

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Unique answer identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "When the answer was created", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "When the answer was last updated", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;

    @NotNull(message = "Question ID is required")
    @Schema(description = "ID of the question being answered", required = true)
    private UUID questionId;

    @NotNull(message = "Answer type is required")
    @Schema(description = "Type of answer", example = "TEXT", required = true)
    private AnswerType type;

    @NotBlank(message = "Answer content is required")
    @Size(min = 1, max = 10000, message = "Content must be between 1 and 10000 characters")
    @Schema(description = "The answer content", example = "Paris", required = true)
    private String content;

    @Schema(description = "Programming language for code answers")
    private String language;

    @Schema(description = "Username of the person submitting the answer")
    private String submittedBy;

    @JsonProperty("isCorrect")
    @Schema(description = "Whether the answer is correct")
    private Boolean isCorrect;

    @Schema(description = "Score received")
    private Double score;

    @Schema(description = "Maximum possible score")
    private Double maxScore;

    @Schema(description = "Feedback on the answer")
    private String feedback;

    @Schema(description = "Whether the answer is active")
    private Boolean isActive;

    @Schema(description = "Media files attached to the answer")
    private List<MultipartFile> mediaFiles;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Media file paths for answer attachments (JSON array)", accessMode = Schema.AccessMode.READ_ONLY)
    private String mediaFilePaths;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Plagiarism similarity score", accessMode = Schema.AccessMode.READ_ONLY)
    private Double plagiarismScore;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Whether the answer is flagged as plagiarized", accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean isPlagiarized;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Detailed plagiarism analysis results", accessMode = Schema.AccessMode.READ_ONLY)
    private String plagiarismDetails;

    public AnswerDTO() {}

    public AnswerDTO(UUID questionId, AnswerType type, String content) {
        this.questionId = questionId;
        this.type = type;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public AnswerType getType() {
        return type;
    }

    public void setType(AnswerType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(Boolean correct) {
        this.isCorrect = correct;
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

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Boolean isActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        this.isActive = active;
    }

    public List<MultipartFile> getMediaFiles() {
        return mediaFiles;
    }

    public void setMediaFiles(List<MultipartFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
    }

    public String getMediaFilePaths() {
        return mediaFilePaths;
    }

    public void setMediaFilePaths(String mediaFilePaths) {
        this.mediaFilePaths = mediaFilePaths;
    }

    public Double getPlagiarismScore() {
        return plagiarismScore;
    }

    public void setPlagiarismScore(Double plagiarismScore) {
        this.plagiarismScore = plagiarismScore;
    }

    public Boolean isPlagiarized() {
        return isPlagiarized;
    }

    public void setPlagiarized(Boolean plagiarized) {
        this.isPlagiarized = plagiarized;
    }

    public String getPlagiarismDetails() {
        return plagiarismDetails;
    }

    public void setPlagiarismDetails(String plagiarismDetails) {
        this.plagiarismDetails = plagiarismDetails;
    }

    public static AnswerDTO from(Answer answer) {
        AnswerDTO dto = new AnswerDTO();
        dto.setId(answer.getId());
        dto.setQuestionId(answer.getQuestionId());
        dto.setType(answer.getType());
        dto.setContent(answer.getContent());
        dto.setMediaFilePaths(answer.getMediaFilePaths());
        dto.setLanguage(answer.getLanguage());
        dto.setCorrect(answer.isCorrect());
        dto.setScore(answer.getScore());
        dto.setMaxScore(answer.getMaxScore());
        dto.setFeedback(answer.getFeedback());
        dto.setSubmittedBy(answer.getSubmittedBy());
        dto.setActive(answer.isActive());
        dto.setCreatedAt(answer.getCreatedAt());
        dto.setUpdatedAt(answer.getUpdatedAt());
        dto.setPlagiarismScore(answer.getPlagiarismScore());
        dto.setPlagiarized(answer.isPlagiarized());
        dto.setPlagiarismDetails(answer.getPlagiarismDetails());
        return dto;
    }

    @Override
    public String toString() {
        return "AnswerDTO{" +
                "id=" + id +
                ", questionId=" + questionId +
                ", type=" + type +
                ", content='" + content + '\'' +
                ", language='" + language + '\'' +
                ", isCorrect=" + isCorrect +
                ", score=" + score +
                ", maxScore=" + maxScore +
                ", feedback='" + feedback + '\'' +
                ", submittedBy='" + submittedBy + '\'' +
                ", isActive=" + isActive +
                ", mediaFiles=" + (mediaFiles != null ? mediaFiles.size() : 0) + " files" +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
