package com.questionbank.QuestionBank.exception;

// Validation utility class with static methods for input validation
public class Validation {

    public static void notNullOrEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(String.format("%s cannot be null or empty", fieldName));
        }
    }

    public static void notNullOrEmptyMedia(java.util.Collection<?> value, String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new ValidationException(String.format("%s cannot be null or empty", fieldName));
        }
    }

    public static void minLength(String value, String fieldName, int minLength) {
        notNullOrEmpty(value, fieldName);
        if (value.length() < minLength) {
            throw new ValidationException(String.format("%s must be at least %d characters long", fieldName, minLength));
        }
    }

    public static void maxLength(String value, String fieldName, int maxLength) {
        notNullOrEmpty(value, fieldName);
        if (value.length() > maxLength) {
            throw new ValidationException(String.format("%s must not exceed %d characters", fieldName, maxLength));
        }
    }

    public static void inRange(int value, String fieldName, int min, int max) {
        if (value < min || value > max) {
            throw new ValidationException(String.format("%s must be between %d and %d", fieldName, min, max));
        }
    }

    public static void positive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(String.format("%s must be positive", fieldName));
        }
    }

    public static void positive(Double value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ValidationException(String.format("%s must be positive", fieldName));
        }
    }

    public static void validateQuestionType(String questionType) {
        notNullOrEmpty(questionType, "questionType");
        try {
            com.questionbank.QuestionBank.entity.QuestionType.fromString(questionType);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Question type must be one of: " +
                java.util.Arrays.toString(com.questionbank.QuestionBank.entity.QuestionType.values()));
        }
    }

    public static void validateQuestionContent(String content, com.questionbank.QuestionBank.entity.QuestionType questionType) {
        notNullOrEmpty(content, "content");
        minLength(content, "content", 10);
        maxLength(content, "content", 10000);
    }

    public static void validateQuestionConfiguration(String configurationData, com.questionbank.QuestionBank.entity.QuestionType questionType) {
        if (configurationData == null || configurationData.trim().isEmpty()) {
            throw new ValidationException("Configuration data cannot be null or empty for question type: " + questionType);
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode config = mapper.readTree(configurationData);

        switch (questionType) {
            case MCQ:
                    validateMCQConfiguration(config);
                break;
            case TRUE_FALSE:
                    validateTrueFalseConfiguration(config);
                break;
            case ESSAY_SHORT:
            case ESSAY_LONG:
                    validateEssayConfiguration(config, questionType);
                break;
            case FILL_IN_BLANK:
                    validateFillInBlankConfiguration(config);
                break;
            case MATCHING:
                    validateMatchingConfiguration(config);
                break;
            case REARRANGE:
                    validateRearrangeConfiguration(config);
                break;
            case SLIDER:
                    validateSliderConfiguration(config);
                break;
            case SELECT_ON_PHOTO:
                    validateSelectOnPhotoConfiguration(config);
                break;
            case PUZZLE:
                    validatePuzzleConfiguration(config);
                break;
            case CODING:
                    validateCodingConfiguration(config);
                break;
            default:

                break;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Configuration data must be valid JSON format for question type: " + questionType);
        }
    }

    // Validate MCQ configuration has required fields and valid option count
    private static void validateMCQConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("options")) {
            throw new ValidationException("MCQ configuration must contain 'options' field");
        }

        com.fasterxml.jackson.databind.JsonNode options = config.get("options");
        if (!options.isObject()) {
            throw new ValidationException("MCQ options must be an object");
        }

        int optionCount = 0;
        int correctCount = 0;

        // Count options and correct answers
        for (com.fasterxml.jackson.databind.JsonNode option : options) {
            optionCount++;
            if (!option.has("text") || !option.has("correct")) {
                throw new ValidationException("Each MCQ option must have 'text' and 'correct' fields");
            }

            String text = option.get("text").asText();
            if (text == null || text.trim().isEmpty()) {
                throw new ValidationException("MCQ option text cannot be empty");
            }

            if (option.get("correct").asBoolean()) {
                correctCount++;
            }
        }

        if (optionCount < 2) {
            throw new ValidationException("MCQ must have at least 2 options");
        }

        if (optionCount > 10) {
            throw new ValidationException("MCQ cannot have more than 10 options");
        }

        if (correctCount == 0) {
            throw new ValidationException("MCQ must have at least one correct answer");
        }

        if (config.has("correctChoicesCount")) {
            int expectedCorrectCount = config.get("correctChoicesCount").asInt();
            if (expectedCorrectCount != correctCount) {
                throw new ValidationException("Correct choices count mismatch");
            }
        }
    }

    private static void validateTrueFalseConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("correctAnswer")) {
            throw new ValidationException("True/False configuration must contain 'correctAnswer' field");
        }

        if (!config.get("correctAnswer").isBoolean()) {
            throw new ValidationException("True/False correctAnswer must be a boolean value (true or false)");
        }
    }

    private static void validateEssayConfiguration(com.fasterxml.jackson.databind.JsonNode config, com.questionbank.QuestionBank.entity.QuestionType questionType) {
        if (!config.has("minWords") || !config.has("maxWords")) {
            throw new ValidationException("Essay configuration must contain 'minWords' and 'maxWords' fields");
        }

        int minWords = config.get("minWords").asInt();
        int maxWords = config.get("maxWords").asInt();

        if (minWords < 10 || minWords > 5000) {
            throw new ValidationException("Minimum words must be between 10 and 5000");
        }

        if (maxWords < 10 || maxWords > 5000) {
            throw new ValidationException("Maximum words must be between 10 and 5000");
        }

        if (minWords >= maxWords) {
            throw new ValidationException("Minimum words must be less than maximum words");
        }

        if (config.has("aiFeedback")) {
            String aiFeedback = config.get("aiFeedback").asText();
            if (!aiFeedback.equals("detailed") && !aiFeedback.equals("brief") && !aiFeedback.equals("minimal")) {
                throw new ValidationException("AI feedback level must be 'detailed', 'brief', or 'minimal'");
            }
        }
    }

    private static void validateFillInBlankConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("sentence")) {
            throw new ValidationException("Fill in Blank configuration must contain 'sentence' field");
        }

        String sentence = config.get("sentence").asText();
        if (sentence == null || sentence.trim().isEmpty()) {
            throw new ValidationException("Fill in Blank sentence cannot be empty");
        }

        if (!sentence.contains("___")) {
            throw new ValidationException("Fill in Blank sentence must contain blanks (___)");
        }

        if (!config.has("blanks") || !config.get("blanks").isArray()) {
            throw new ValidationException("Fill in Blank configuration must contain 'blanks' array");
        }

        com.fasterxml.jackson.databind.JsonNode blanks = config.get("blanks");
        if (blanks.size() == 0) {
            throw new ValidationException("Fill in Blank must have at least one blank");
        }

        for (com.fasterxml.jackson.databind.JsonNode blank : blanks) {
            if (!blank.has("position") || !blank.has("correctAnswers")) {
                throw new ValidationException("Each blank must have 'position' and 'correctAnswers' fields");
            }

            if (!blank.get("correctAnswers").isArray()) {
                throw new ValidationException("Blank correctAnswers must be an array");
            }

            if (blank.get("correctAnswers").size() == 0) {
                throw new ValidationException("Each blank must have at least one correct answer");
            }
        }

        if (config.has("blankCount")) {
            int expectedCount = config.get("blankCount").asInt();
            if (expectedCount != blanks.size()) {
                throw new ValidationException("Blank count mismatch");
            }
        }
    }

    private static void validateMatchingConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("pairs") || !config.get("pairs").isArray()) {
            throw new ValidationException("Matching configuration must contain 'pairs' array");
        }

        com.fasterxml.jackson.databind.JsonNode pairs = config.get("pairs");
        if (pairs.size() == 0) {
            throw new ValidationException("Matching must have at least one pair");
        }

        if (pairs.size() > 20) {
            throw new ValidationException("Matching cannot have more than 20 pairs");
        }

        int columnCount = config.has("columnCount") ? config.get("columnCount").asInt() : 2;
        if (columnCount < 2 || columnCount > 4) {
            throw new ValidationException("Matching column count must be between 2 and 4");
        }

        for (com.fasterxml.jackson.databind.JsonNode pair : pairs) {
            boolean hasData = false;
            for (int i = 1; i <= columnCount; i++) {
                String columnKey = "column_" + i;
                if (pair.has(columnKey)) {
                    String value = pair.get(columnKey).asText();
                    if (value != null && !value.trim().isEmpty()) {
                        hasData = true;
                    }
                }
            }

            if (!hasData) {
                throw new ValidationException("Each matching pair must have at least one non-empty column");
            }
        }
    }

    private static void validateRearrangeConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("correctOrder") || !config.get("correctOrder").isArray()) {
            throw new ValidationException("Rearrange configuration must contain 'correctOrder' array");
        }

        com.fasterxml.jackson.databind.JsonNode correctOrder = config.get("correctOrder");
        if (correctOrder.size() < 2) {
            throw new ValidationException("Rearrange must have at least 2 items to order");
        }

        if (correctOrder.size() > 20) {
            throw new ValidationException("Rearrange cannot have more than 20 items");
        }

        for (com.fasterxml.jackson.databind.JsonNode item : correctOrder) {
            String itemText = item.asText();
            if (itemText == null || itemText.trim().isEmpty()) {
                throw new ValidationException("Rearrange items cannot be empty");
            }
        }

        if (config.has("itemCount")) {
            int expectedCount = config.get("itemCount").asInt();
            if (expectedCount != correctOrder.size()) {
                throw new ValidationException("Item count mismatch");
            }
        }
    }

    private static void validateSliderConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("minValue") || !config.has("maxValue") || !config.has("correctValue")) {
            throw new ValidationException("Slider configuration must contain 'minValue', 'maxValue', and 'correctValue' fields");
        }

        double minValue = config.get("minValue").asDouble();
        double maxValue = config.get("maxValue").asDouble();
        double correctValue = config.get("correctValue").asDouble();

        if (minValue >= maxValue) {
            throw new ValidationException("Slider minimum value must be less than maximum value");
        }

        if (correctValue < minValue || correctValue > maxValue) {
            throw new ValidationException("Slider correct value must be within the min-max range");
        }

        String answerType = config.has("answerType") ? config.get("answerType").asText() : "withData";
        if (!answerType.equals("withData") && !answerType.equals("withoutData")) {
            throw new ValidationException("Slider answer type must be 'withData' or 'withoutData'");
        }

        if (answerType.equals("withData")) {
            if (!config.has("leftAnswer") || !config.has("rightAnswer")) {
                throw new ValidationException("Slider withData type must have 'leftAnswer' and 'rightAnswer' fields");
            }

            String leftAnswer = config.get("leftAnswer").asText();
            String rightAnswer = config.get("rightAnswer").asText();

            if (leftAnswer == null || leftAnswer.trim().isEmpty()) {
                throw new ValidationException("Slider left answer cannot be empty");
            }

            if (rightAnswer == null || rightAnswer.trim().isEmpty()) {
                throw new ValidationException("Slider right answer cannot be empty");
            }
        }
    }

    private static void validateSelectOnPhotoConfiguration(com.fasterxml.jackson.databind.JsonNode config) {

        if (!config.has("gridRows") || !config.has("gridCols")) {
            throw new ValidationException("Select on Photo configuration must contain 'gridRows' and 'gridCols' fields");
        }

        int gridRows = config.get("gridRows").asInt();
        int gridCols = config.get("gridCols").asInt();

        if (gridRows < 1 || gridRows > 10) {
            throw new ValidationException("Grid rows must be between 1 and 10");
        }

        if (gridCols < 1 || gridCols > 10) {
            throw new ValidationException("Grid columns must be between 1 and 10");
        }

        if (!config.has("imageWidth") || !config.has("imageHeight")) {
            throw new ValidationException("Select on Photo configuration must contain 'imageWidth' and 'imageHeight' fields");
        }

        int imageWidth = config.get("imageWidth").asInt();
        int imageHeight = config.get("imageHeight").asInt();

        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new ValidationException("Image dimensions must be positive");
        }

        if (!config.has("selectedBlocks") || !config.get("selectedBlocks").isArray()) {
            throw new ValidationException("Select on Photo configuration must contain 'selectedBlocks' array");
        }

        com.fasterxml.jackson.databind.JsonNode selectedBlocks = config.get("selectedBlocks");
        if (selectedBlocks.size() == 0) {
            throw new ValidationException("Select on Photo must have at least one selected block");
        }

        if (selectedBlocks.size() > 20) {
            throw new ValidationException("Select on Photo cannot have more than 20 selected blocks");
        }

        for (com.fasterxml.jackson.databind.JsonNode block : selectedBlocks) {
            String blockId = block.asText();
            if (blockId == null || blockId.length() < 2) {
                throw new ValidationException("Invalid block ID format: " + blockId);
            }
        }
    }

    private static void validatePuzzleConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("correctAnswer") || !config.get("correctAnswer").isArray()) {
            throw new ValidationException("Puzzle configuration must contain 'correctAnswer' array");
        }

        com.fasterxml.jackson.databind.JsonNode correctAnswer = config.get("correctAnswer");
        if (correctAnswer.size() < 2) {
            throw new ValidationException("Puzzle must have at least 2 pieces");
        }

        if (correctAnswer.size() > 20) {
            throw new ValidationException("Puzzle cannot have more than 20 pieces");
        }

        for (com.fasterxml.jackson.databind.JsonNode piece : correctAnswer) {
            String pieceText = piece.asText();
            if (pieceText == null || pieceText.trim().isEmpty()) {
                throw new ValidationException("Puzzle pieces cannot be empty");
            }
        }
    }

    // Validate coding question has language, time limit, and test cases
    private static void validateCodingConfiguration(com.fasterxml.jackson.databind.JsonNode config) {
        if (!config.has("language")) {
            throw new ValidationException("Coding configuration must contain 'language' field");
        }

        String language = config.get("language").asText();
        String[] validLanguages = {"java", "cpp", "c", "csharp", "html", "python", "assembly", "sudo"};
        boolean validLanguage = false;
        for (String validLang : validLanguages) {
            if (validLang.equals(language)) {
                validLanguage = true;
                break;
            }
        }

        if (!validLanguage) {
            throw new ValidationException("Invalid programming language. Must be one of: " +
                java.util.Arrays.toString(validLanguages));
        }

        if (!config.has("timeLimit")) {
            throw new ValidationException("Coding configuration must contain 'timeLimit' field");
        }

        int timeLimit = config.get("timeLimit").asInt();
        if (timeLimit < 30 || timeLimit > 3600) {
            throw new ValidationException("Coding time limit must be between 30 and 3600 seconds");
        }

        if (!config.has("testCases")) {
            throw new ValidationException("Coding configuration must contain 'testCases' field");
        }

        String testCasesJson = config.get("testCases").asText();
        if (testCasesJson == null || testCasesJson.trim().isEmpty()) {
            throw new ValidationException("Coding test cases cannot be empty");
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode testCases = mapper.readTree(testCasesJson);

            if (!testCases.isArray()) {
                throw new ValidationException("Coding test cases must be a JSON array");
            }

            if (testCases.size() == 0) {
                throw new ValidationException("Coding must have at least one test case");
            }

            if (testCases.size() > 20) {
                throw new ValidationException("Coding cannot have more than 20 test cases");
            }

            for (com.fasterxml.jackson.databind.JsonNode testCase : testCases) {
                if (!testCase.has("input") || !testCase.has("output")) {
                    throw new ValidationException("Each test case must have 'input' and 'output' fields");
                }

                String input = testCase.get("input").asText();
                String output = testCase.get("output").asText();

                if (input == null || input.trim().isEmpty()) {
                    throw new ValidationException("Test case input cannot be empty");
                }

                if (output == null || output.trim().isEmpty()) {
                    throw new ValidationException("Test case output cannot be empty");
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Coding test cases must be valid JSON format");
        }
    }

    public static void validateConfigurationData(String configurationData) {
        if (configurationData == null || configurationData.trim().isEmpty()) {
            return;
        }

        try {

            new com.fasterxml.jackson.databind.ObjectMapper().readTree(configurationData);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Configuration data must be valid JSON format");
        }
    }

    public static void validateMediaFilePaths(String mediaFilePaths) {
        if (mediaFilePaths == null || mediaFilePaths.trim().isEmpty()) {
            return;
        }

        try {

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readValue(mediaFilePaths, String[].class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Media file paths must be a valid JSON array of strings");
        }
    }

    public static void validateAllowedMediaTypes(String allowedMediaTypes) {
        if (allowedMediaTypes == null || allowedMediaTypes.trim().isEmpty()) {
            return;
        }

        try {

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String[] types = mapper.readValue(allowedMediaTypes, String[].class);

            for (String type : types) {
                if (type == null || type.trim().isEmpty()) {
                    throw new ValidationException("Media type cannot be null or empty");
                }

                if (!type.contains("/") && !type.matches("^[a-zA-Z0-9_-]+$")) {
                    throw new ValidationException("Invalid media type format: " + type + ". Expected format: type/subtype or simple type name");
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ValidationException("Allowed media types must be a valid JSON array of strings");
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String resourceType, String resourceId) {
            super(String.format("%s not found with id: %s", resourceType, resourceId));
        }

        public ResourceNotFoundException(String resourceType, String resourceId, String customMessage) {
            super(customMessage);
        }
    }
}
