package com.questionbank.QuestionBank.dto;

import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.entity.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Data transfer object for question requests and responses
@Schema(description = "DTO for question operations (create, update, response)")
public class QuestionDTO {

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Unique question identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "When the question was created", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "When the question was last updated", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "User who created the question", accessMode = Schema.AccessMode.READ_ONLY)
    private String createdBy;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "User who last updated the question", accessMode = Schema.AccessMode.READ_ONLY)
    private String updatedBy;

    @NotBlank(message = "Question title is required")
    @Size(min = 3, max = 500, message = "Title must be between 3 and 500 characters")
    @Schema(description = "The title of the question", example = "What is the capital of France?", required = true)
    private String title;

    @NotNull(message = "Question type is required")
    @Schema(description = "The type of the question", example = "MCQ", required = true)
    private QuestionType type;

    @NotBlank(message = "Module is required")
    @Size(min = 2, max = 100, message = "Module must be between 2 and 100 characters")
    @Schema(description = "The module this question belongs to", example = "Geography", required = true)
    private String module;

    @NotBlank(message = "Unit is required")
    @Size(min = 2, max = 100, message = "Unit must be between 2 and 100 characters")
    @Schema(description = "The unit within the module", example = "European Capitals", required = true)
    private String unit;

    @NotBlank(message = "Configuration data is required")
    @Schema(description = "Configuration data for the question (JSON format)",
            example = "{\"options\": {\"option_1\": {\"id\": \"option_1\", \"text\": \"A\", \"isCorrect\": true, \"order\": 1}, \"option_2\": {\"id\": \"option_2\", \"text\": \"B\", \"isCorrect\": false, \"order\": 2}}}", required = true)
    private String configurationData;

    @Schema(description = "The detailed content of the question", example = "Choose the correct capital city of France from the options below.")
    private String content;

    @Min(value = 1, message = "Difficulty level must be at least 1")
    @Max(value = 10, message = "Difficulty level must not exceed 10")
    @Schema(description = "Difficulty level from 1 to 10", example = "3")
    private Integer difficultyLevel;

    @DecimalMin(value = "0.0", message = "Points must be non-negative")
    @DecimalMax(value = "1000.0", message = "Points must not exceed 1000")
    @Schema(description = "Points awarded for correct answer", example = "10.0")
    private Double points;

    @Min(value = 1, message = "Time limit must be at least 1 minute")
    @Max(value = 1440, message = "Time limit must not exceed 1440 minutes (24 hours)")
    @Schema(description = "Time limit in minutes", example = "15")
    private Integer timeLimitMinutes;

    @Schema(description = "Whether to allow media insertion for students", example = "true")
    private Boolean allowMediaInsertion = false;

    @Schema(description = "Allowed media types for students (JSON array)", example = "[\"image/png\", \"image/jpg\", \"video/mp4\"]")
    private String allowedMediaTypes = "[]";

    @Schema(description = "Whether the question is active")
    private Boolean isActive;

    @Schema(description = "Media files attached to the question")
    private List<MultipartFile> mediaFiles;

    @JsonProperty(access = Access.READ_ONLY)
    @Schema(description = "Media file paths for question attachments (JSON array)", accessMode = Schema.AccessMode.READ_ONLY)
    private String mediaFilePaths;

    public QuestionDTO() {}

    public QuestionDTO(String title, QuestionType type, String module, String unit, String configurationData) {
        this.title = title;
        this.type = type;
        this.module = module;
        this.unit = unit;
        this.configurationData = configurationData;
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
        return configurationData;
    }

    public void setConfigurationData(String configurationData) {
        this.configurationData = configurationData;
    }

    public Boolean getAllowMediaInsertion() {
        return allowMediaInsertion;
    }

    public void setAllowMediaInsertion(Boolean allowMediaInsertion) {
        this.allowMediaInsertion = allowMediaInsertion;
    }

    public String getAllowedMediaTypes() {
        return allowedMediaTypes;
    }

    public void setAllowedMediaTypes(String allowedMediaTypes) {
        this.allowedMediaTypes = allowedMediaTypes;
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

    public static QuestionDTO from(Question question) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setTitle(question.getTitle());
        dto.setType(question.getType());
        dto.setModule(question.getModule());
        dto.setUnit(question.getUnit());
        dto.setContent(question.getContent());
        dto.setMediaFilePaths(question.getMediaFilePaths());
        dto.setAllowMediaInsertion(question.getAllowMediaInsertion());
        dto.setAllowedMediaTypes(question.getAllowedMediaTypes());
        dto.setDifficultyLevel(question.getDifficultyLevel());
        dto.setPoints(question.getPoints());
        dto.setTimeLimitMinutes(question.getTimeLimitMinutes());
        dto.setConfigurationData(question.getConfigurationData());
        dto.setActive(question.isActive());
        dto.setCreatedAt(question.getCreatedAt());
        dto.setUpdatedAt(question.getUpdatedAt());
        dto.setCreatedBy(question.getCreatedBy());
        dto.setUpdatedBy(question.getUpdatedBy());
        return dto;
    }

    @Override
    public String toString() {
        return "QuestionDTO{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", module='" + module + '\'' +
                ", unit='" + unit + '\'' +
                ", content='" + content + '\'' +
                ", difficultyLevel=" + difficultyLevel +
                ", points=" + points +
                ", timeLimitMinutes=" + timeLimitMinutes +
                ", configurationData='" + configurationData + '\'' +
                ", allowMediaInsertion=" + allowMediaInsertion +
                ", allowedMediaTypes='" + allowedMediaTypes + '\'' +
                ", isActive=" + isActive +
                ", mediaFiles=" + (mediaFiles != null ? mediaFiles.size() : 0) + " files" +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
