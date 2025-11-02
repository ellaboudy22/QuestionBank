package com.questionbank.QuestionBank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// Entity representing a student's answer with scoring and plagiarism data
@Entity
@Table(name = "answers", indexes = {
    @Index(name = "idx_answer_question_id", columnList = "question_id"),
    @Index(name = "idx_answer_type", columnList = "type"),
    @Index(name = "idx_answer_created_at", columnList = "created_at")
})
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Question ID is required")
    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @NotNull(message = "Answer type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AnswerType type;

    @NotBlank(message = "Answer content is required")
    @Size(min = 1, max = 10000, message = "Content must be between 1 and 10000 characters")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_file_paths", columnDefinition = "TEXT")
    private String mediaFilePaths = "[]";

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @DecimalMin(value = "0.0", message = "Score must be non-negative")
    @Column(name = "score")
    private Double score;

    @DecimalMin(value = "0.0", message = "Max score must be non-negative")
    @Column(name = "max_score")
    private Double maxScore;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "image_embeddings", columnDefinition = "TEXT")
    private String imageEmbeddings;

    @Column(name = "plagiarism_score")
    private Double plagiarismScore;

    @Column(name = "is_plagiarized")
    private Boolean isPlagiarized = false;

    @Column(name = "plagiarism_details", columnDefinition = "TEXT")
    private String plagiarismDetails;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Answer() {
        this.isActive = true;
        this.mediaFilePaths = "[]";
    }

    public Answer(UUID questionId, AnswerType type, String content, String mediaFilePaths,
                  String language, Boolean isCorrect, Double score, Double maxScore,
                  String feedback, String submittedBy, Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.questionId = questionId;
        this.type = type;
        this.content = content;
        this.mediaFilePaths = mediaFilePaths != null ? mediaFilePaths : "[]";
        this.language = language;
        this.isCorrect = isCorrect;
        this.score = score;
        this.maxScore = maxScore;
        this.feedback = feedback;
        this.submittedBy = submittedBy;
        this.isActive = isActive != null ? isActive : true;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getMediaFilePaths() {
        return mediaFilePaths != null ? mediaFilePaths : "[]";
    }

    public void setMediaFilePaths(String mediaFilePaths) {
        this.mediaFilePaths = mediaFilePaths != null ? mediaFilePaths : "[]";
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
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

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Boolean isActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        this.isActive = active;
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

    public String getImageEmbeddings() {
        return imageEmbeddings;
    }

    public void setImageEmbeddings(String imageEmbeddings) {
        this.imageEmbeddings = imageEmbeddings;
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

    @Override
    public String toString() {
        return "Answer{" +
                "id=" + id +
                ", questionId=" + questionId +
                ", type=" + type +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Answer answer = (Answer) o;
        return id != null && id.equals(answer.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
