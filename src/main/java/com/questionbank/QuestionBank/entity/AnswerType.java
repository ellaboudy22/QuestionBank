package com.questionbank.QuestionBank.entity;

// Enum for all supported answer submission types
public enum AnswerType {
    MULTIPLE_CHOICE("Multiple Choice"),
    FILL_IN_BLANK("Fill in the Blank"),
    SHORT_ANSWER("Short Answer"),
    LONG_ANSWER("Long Answer"),
    VIDEO_ANSWER("Video Answer"),
    VOICE_ANSWER("Voice Answer"),
    PDF_UPLOAD("PDF Upload"),
    IMAGE_UPLOAD("Image Upload"),
    VIDEO_UPLOAD("Video Upload"),
    VOICE_UPLOAD("Voice Upload"),
    CODE_SUBMISSION("Code Submission"),
    TRUE_FALSE("True/False"),
    MATCHING("Matching"),
    REARRANGE("Rearrange"),
    SLIDER("Slider"),
    PUZZLE("Puzzle");

    private final String displayName;

    AnswerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AnswerType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (AnswerType type : AnswerType.values()) {
            if (type.name().equalsIgnoreCase(value) ||
                type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid answer type: " + value);
    }

    public boolean isCodeSubmission() {
        return this == CODE_SUBMISSION;
    }
}
