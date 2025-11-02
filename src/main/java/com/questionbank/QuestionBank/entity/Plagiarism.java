package com.questionbank.QuestionBank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// Entity for tracking submitted media files for plagiarism detection
@Entity
@Table(name = "plagiarism", indexes = {
    @Index(name = "idx_plagiarism_user", columnList = "uploaded_by"),
    @Index(name = "idx_plagiarism_type", columnList = "type"),
    @Index(name = "idx_plagiarism_question_id", columnList = "question_id"),
    @Index(name = "idx_plagiarism_answer_id", columnList = "answer_id"),
    @Index(name = "idx_plagiarism_created_at", columnList = "created_at")
})
public class Plagiarism {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Media path is required")
    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @NotBlank(message = "Media type is required")
    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @NotBlank(message = "User is required")
    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String user;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Plagiarism(String path, String type, String user, UUID questionId, UUID answerId) {
        this.path = path;
        this.type = type;
        this.user = user;
        this.questionId = questionId;
        this.answerId = answerId;
    }

    public Plagiarism() {
        this(null, null, null, null, null);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
    }

    public UUID getAnswerId() {
        return answerId;
    }

    public void setAnswerId(UUID answerId) {
        this.answerId = answerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Plagiarism{" +
                "id=" + id +
                ", path='" + path + '\'' +
                ", type='" + type + '\'' +
                ", user='" + user + '\'' +
                ", questionId=" + questionId +
                ", answerId=" + answerId +
                ", createdAt=" + createdAt +
                '}';
    }
}
