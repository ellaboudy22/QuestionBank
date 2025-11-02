package com.questionbank.QuestionBank.entity;

// Enum for all supported question types
public enum QuestionType {

    MCQ("Multiple Choice Question"),

    TRUE_FALSE("True/False"),

    ESSAY_SHORT("Short Essay"),

    ESSAY_LONG("Long Essay"),

    FILL_IN_BLANK("Fill in the Blank"),

    MATCHING("Matching"),

    REARRANGE("Rearrange"),

    SLIDER("Slider"),

    SELECT_ON_PHOTO("Select on Photo"),

    PUZZLE("Puzzle"),

    CODING("Coding");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static QuestionType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (QuestionType type : QuestionType.values()) {
            if (type.name().equalsIgnoreCase(value) ||
                type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid question type: " + value);
    }

    public boolean requiresPresetAnswers() {
        return this == MCQ || this == TRUE_FALSE || this == FILL_IN_BLANK ||
               this == MATCHING || this == REARRANGE || this == SLIDER ||
               this == SELECT_ON_PHOTO || this == PUZZLE;
    }
}
