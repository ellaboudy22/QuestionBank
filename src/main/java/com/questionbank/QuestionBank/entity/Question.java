package com.questionbank.QuestionBank.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// Entity representing a question with configuration and media files
@Entity
@Table(name = "questions", indexes = {
    @Index(name = "idx_question_type", columnList = "type"),
    @Index(name = "idx_question_module", columnList = "module"),
    @Index(name = "idx_question_unit", columnList = "unit"),
    @Index(name = "idx_question_module_unit", columnList = "module, unit")
})
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Question title is required")
    @Size(min = 3, max = 500, message = "Title must be between 3 and 500 characters")
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @NotNull(message = "Question type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private QuestionType type;

    @NotBlank(message = "Module is required")
    @Size(min = 2, max = 100, message = "Module must be between 2 and 100 characters")
    @Column(name = "module", nullable = false, length = 100)
    private String module;

    @NotBlank(message = "Unit is required")
    @Size(min = 2, max = 100, message = "Unit must be between 2 and 100 characters")
    @Column(name = "unit", nullable = false, length = 100)
    private String unit;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_file_paths", columnDefinition = "TEXT")
    private String mediaFilePaths = "[]";

    @Column(name = "allow_media_insertion", nullable = false)
    private Boolean allowMediaInsertion = false;

    @Column(name = "allowed_media_types", columnDefinition = "TEXT")
    private String allowedMediaTypes = "[]";

    @Min(value = 1, message = "Difficulty level must be at least 1")
    @Max(value = 10, message = "Difficulty level must be at most 10")
    @Column(name = "difficulty_level")
    private Integer difficultyLevel;

    @DecimalMin(value = "0.0", message = "Points must be non-negative")
    @Column(name = "points")
    private Double points;

    @Min(value = 1, message = "Time limit must be at least 1 minute")
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "configuration_data", columnDefinition = "TEXT")
    private String configurationData = "{}";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public Question(String title, QuestionType type, String module, String unit,
                   String content, String mediaFilePaths, Boolean allowMediaInsertion,
                   String allowedMediaTypes, Integer difficultyLevel, Double points,
                   Integer timeLimitMinutes, String configurationData, Boolean isActive,
                   String createdBy, String updatedBy) {
        this.title = title;
        this.type = type;
        this.module = module;
        this.unit = unit;
        this.content = content;
        this.mediaFilePaths = mediaFilePaths != null ? mediaFilePaths : "[]";
        this.allowMediaInsertion = allowMediaInsertion != null ? allowMediaInsertion : false;
        this.allowedMediaTypes = allowedMediaTypes != null ? allowedMediaTypes : "[]";
        this.difficultyLevel = difficultyLevel;
        this.points = points;
        this.timeLimitMinutes = timeLimitMinutes;
        this.configurationData = configurationData != null ? configurationData : "{}";
        this.isActive = isActive != null ? isActive : true;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public Question() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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

    public Boolean getAllowMediaInsertion() {
        return allowMediaInsertion;
    }

    public void setAllowMediaInsertion(Boolean allowMediaInsertion) {
        this.allowMediaInsertion = allowMediaInsertion != null ? allowMediaInsertion : false;
    }

    public String getAllowedMediaTypes() {
        return allowedMediaTypes != null ? allowedMediaTypes : "[]";
    }

    public void setAllowedMediaTypes(String allowedMediaTypes) {
        this.allowedMediaTypes = allowedMediaTypes != null ? allowedMediaTypes : "[]";
    }

    public Integer getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(Integer difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public Double getPoints() {
        return points;
    }

    public void setPoints(Double points) {
        this.points = points;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public String getConfigurationData() {
        return configurationData != null ? configurationData : "{}";
    }

    public void setConfigurationData(String configurationData) {
        this.configurationData = configurationData != null ? configurationData : "{}";
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", module='" + module + '\'' +
                ", unit='" + unit + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Question question = (Question) o;
        return id != null && id.equals(question.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
