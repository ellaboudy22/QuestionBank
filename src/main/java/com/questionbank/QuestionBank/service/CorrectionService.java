package com.questionbank.QuestionBank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questionbank.QuestionBank.entity.Answer;
import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.entity.QuestionType;
import com.questionbank.QuestionBank.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// Service for automatic answer scoring and correction
@Service
public class CorrectionService {

    private static final Logger log = LoggerFactory.getLogger(CorrectionService.class);

    private final ObjectMapper objectMapper;
    private final CodeCompilationService codeCompilationService;
    private final AiCorrectionService aiCorrectionService;

    @Autowired
    public CorrectionService(ObjectMapper objectMapper,
                           CodeCompilationService codeCompilationService,
                           @Lazy AiCorrectionService aiCorrectionService) {
        this.objectMapper = objectMapper;
        this.codeCompilationService = codeCompilationService;
        this.aiCorrectionService = aiCorrectionService;
    }

    private void setAnswerResult(Answer answer, boolean isCorrect, double score, double maxScore, String feedback) {
        answer.setCorrect(isCorrect);
        answer.setScore(score);
        answer.setMaxScore(maxScore);
        answer.setFeedback(feedback);
    }

    private void handleScoringError(Answer answer, Question question, String context) {
        log.error("Error processing {} answer", context);
        setAnswerResult(answer, false, 0.0, question.getPoints(),
            "Error processing answer. Please contact your instructor.");
    }

    private String buildPartialFeedback(int correctCount, int totalCount, double score, double maxScore,
                                       List<String> correctDetails, List<String> incorrectDetails,
                                       List<String> missingDetails) {
        StringBuilder feedback = new StringBuilder();
        feedback.append(String.format("You got %d out of %d correct. Score: %.1f/%.1f\n",
            correctCount, totalCount, score, maxScore));

        if (correctDetails != null && !correctDetails.isEmpty()) {
            feedback.append("Correct: ").append(String.join(", ", correctDetails)).append("\n");
        }

        if (incorrectDetails != null && !incorrectDetails.isEmpty()) {
            feedback.append("Incorrect: ").append(String.join(", ", incorrectDetails)).append("\n");
        }

        if (missingDetails != null && !missingDetails.isEmpty()) {
            feedback.append("Missing: ").append(String.join(", ", missingDetails));
        }

        return feedback.toString().trim();
    }

    // Parse JSON array or fallback to comma-separated values
    private List<String> parseJsonArray(String content) {
        List<String> result = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(content.trim());
            if (node.isArray()) {
                for (JsonNode item : node) {
                    result.add(item.asText().trim());
                }
            } else {
                result.add(node.asText().trim());
            }
        } catch (Exception e) {
            for (String part : content.split(",")) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private double calculatePartialScore(int correctCount, int totalCount, double maxPoints) {
        return (double) correctCount / totalCount * maxPoints;
    }

    private JsonNode parseConfig(Question question) throws Exception {
        JsonNode config = Utils.Json.parseConfigurationData(question.getConfigurationData(), objectMapper);
        if (config == null) {
            throw new IllegalArgumentException("Invalid configuration");
        }
        return config;
    }

    public void autoScoreAnswer(Answer answer, Question question) {
        try {
            switch (question.getType()) {
                case MCQ:
                    scoreMCQAnswer(answer, question);
                    break;
                case TRUE_FALSE:
                    scoreTrueFalseAnswer(answer, question);
                    break;
                case MATCHING:
                    scoreMatchingAnswer(answer, question);
                    break;
                case FILL_IN_BLANK:
                    scoreFillInBlankAnswer(answer, question);
                    break;
                case REARRANGE:
                    scoreRearrangeAnswer(answer, question);
                    break;
                case SLIDER:
                    scoreSliderAnswer(answer, question);
                    break;
                case PUZZLE:
                    scorePuzzleAnswer(answer, question);
                    break;
                case SELECT_ON_PHOTO:
                    scoreSelectOnPhotoAnswer(answer, question);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            String answerId = answer.getId() != null ? answer.getId().toString() : "pending";
            log.error("Auto-scoring failed for answer {}: {}", answerId, e.getMessage());
        }
    }

    // Score MCQ answers with partial credit for multi-select questions
    private void scoreMCQAnswer(Answer answer, Question question) {
        try {
            JsonNode configNode = parseConfig(question);
            JsonNode optionsNode = configNode.get("options");
            if (optionsNode == null || !optionsNode.isObject()) return;

            List<String> correctChoiceIds = new ArrayList<>();
            List<String> correctAnswerTexts = new ArrayList<>();
            Map<String, String> choiceIdToTextMap = new HashMap<>();
            Map<String, String> textToChoiceIdMap = new HashMap<>();

            optionsNode.fields().forEachRemaining(entry -> {
                String choiceId = entry.getKey();
                JsonNode option = entry.getValue();
                String optionText = option.get("text").asText();

                choiceIdToTextMap.put(choiceId, optionText);
                textToChoiceIdMap.put(optionText, choiceId);

                if (option.has("correct") && option.get("correct").asBoolean()) {
                    correctChoiceIds.add(choiceId);
                    correctAnswerTexts.add(optionText);
                }
            });

            if (correctChoiceIds.isEmpty()) return;

            List<String> studentChoiceIds = new ArrayList<>();
            try {
                JsonNode studentAnswerArray = objectMapper.readTree(answer.getContent().trim());
                if (studentAnswerArray.isArray()) {
                    studentAnswerArray.forEach(node -> studentChoiceIds.add(node.asText()));
                } else {
                    studentChoiceIds.add(studentAnswerArray.asText());
                }
            } catch (Exception e) {
                for (String studentAns : answer.getContent().split(",")) {
                    String trimmed = studentAns.trim();
                    if (choiceIdToTextMap.containsKey(trimmed)) {
                        studentChoiceIds.add(trimmed);
                    } else if (textToChoiceIdMap.containsKey(trimmed)) {
                        studentChoiceIds.add(textToChoiceIdMap.get(trimmed));
                    }
                }
            }

            // Single-select MCQ: all or nothing scoring
            if (correctChoiceIds.size() == 1) {
                boolean isCorrect = studentChoiceIds.size() == 1 &&
                                  studentChoiceIds.get(0).equals(correctChoiceIds.get(0));
                setAnswerResult(answer, isCorrect, isCorrect ? question.getPoints() : 0.0,
                    question.getPoints(), isCorrect ? "Correct answer!" :
                    "Incorrect answer. The correct answer is: " + correctAnswerTexts.get(0));
            } else {
                // Multi-select MCQ: partial credit based on correct selections
                int correctCount = 0;
                List<String> correctAnswers = new ArrayList<>();
                List<String> incorrectAnswers = new ArrayList<>();
                List<String> missingAnswers = new ArrayList<>();

                for (String studentId : studentChoiceIds) {
                    if (correctChoiceIds.contains(studentId)) {
                        correctCount++;
                        correctAnswers.add(choiceIdToTextMap.get(studentId));
                    } else {
                        incorrectAnswers.add(choiceIdToTextMap.get(studentId));
                    }
                }

                for (String correctId : correctChoiceIds) {
                    if (!studentChoiceIds.contains(correctId)) {
                        missingAnswers.add(choiceIdToTextMap.get(correctId));
                    }
                }

                double score = calculatePartialScore(correctCount, correctChoiceIds.size(), question.getPoints());
                boolean fullyCorrect = correctCount == correctChoiceIds.size() && incorrectAnswers.isEmpty();

                if (fullyCorrect) {
                    setAnswerResult(answer, true, score, question.getPoints(), "Perfect! All answers are correct.");
                } else if (correctCount > 0) {
                    String feedback = buildPartialFeedback(correctCount, correctChoiceIds.size(), score,
                        question.getPoints(), correctAnswers, incorrectAnswers, missingAnswers);
                    setAnswerResult(answer, false, score, question.getPoints(), feedback);
                } else {
                    setAnswerResult(answer, false, 0.0, question.getPoints(),
                        "Incorrect. The correct answers are: " + String.join(", ", correctAnswerTexts));
                }
            }
        } catch (Exception e) {
            handleScoringError(answer, question, "MCQ");
        }
    }

    private void scoreTrueFalseAnswer(Answer answer, Question question) {
        String correctAnswer = extractCorrectAnswersFromQuestion(question);
        if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
            return;
        }

        String studentAnswer = answer.getContent().trim();

        String[] correctAnswers = correctAnswer.split(",");
        String[] studentAnswers = studentAnswer.split(",");

        if (correctAnswers.length == 1) {
            boolean isCorrect = studentAnswer.equalsIgnoreCase(correctAnswer.trim());
            answer.setCorrect(isCorrect);
            answer.setScore(isCorrect ? question.getPoints() : 0.0);
            answer.setMaxScore(question.getPoints());
            answer.setFeedback(isCorrect ? "Correct answer!" : "Incorrect answer. The correct answer is: " + correctAnswer);
        } else {
            boolean allCorrect = true;
            List<String> incorrectStatementDetails = new ArrayList<>();

            for (int i = 0; i < Math.min(correctAnswers.length, studentAnswers.length); i++) {
                String correctAns = correctAnswers[i].trim();
                String studentAns = studentAnswers[i].trim();

                if (!correctAns.equalsIgnoreCase(studentAns)) {
                    allCorrect = false;
                    incorrectStatementDetails.add(String.format("Statement %d: Expected '%s', Got '%s'",
                        i + 1, correctAns, studentAns));
                }
            }

            answer.setCorrect(allCorrect);
            answer.setScore(allCorrect ? question.getPoints() : 0.0);
            answer.setMaxScore(question.getPoints());

            if (allCorrect) {
                answer.setFeedback("Perfect! All statements are correct.");
            } else {
                StringBuilder feedback = new StringBuilder();
                feedback.append("Incorrect. All statements must be correct for full points.\n");
                feedback.append("Incorrect statements:\n");
                for (String detail : incorrectStatementDetails) {
                    feedback.append("  ").append(detail).append("\n");
                }
                answer.setFeedback(feedback.toString().trim());
            }
        }
    }

    private void scoreMatchingAnswer(Answer answer, Question question) {
        try {
            JsonNode configNode = parseConfig(question);
            JsonNode pairsNode = configNode.get("pairs");
            if (pairsNode == null || !pairsNode.isArray()) return;

            JsonNode studentAnswerNode;
            try {
                studentAnswerNode = objectMapper.readTree(answer.getContent().trim());
            } catch (Exception e) {
                setAnswerResult(answer, false, 0.0, question.getPoints(), "Invalid answer format. Expected JSON format.");
                return;
            }

            Map<Integer, JsonNode> correctPairsMap = new HashMap<>();
            pairsNode.forEach(pairNode -> correctPairsMap.put(pairNode.get("pairNumber").asInt(), pairNode));

            int correctMatches = 0;
            List<String> correctDetails = new ArrayList<>();
            List<String> incorrectDetails = new ArrayList<>();

            for (Map.Entry<Integer, JsonNode> entry : correctPairsMap.entrySet()) {
                int pairNum = entry.getKey();
                JsonNode correctPair = entry.getValue();
                JsonNode studentPair = studentAnswerNode.get(String.valueOf(pairNum));

                String correctCol1 = correctPair.get("column_1").asText();
                String correctCol2 = correctPair.get("column_2").asText();
                boolean hasCol3 = correctPair.has("column_3");
                String correctCol3 = hasCol3 ? correctPair.get("column_3").asText() : "";

                if (studentPair != null && studentPair.isObject()) {
                    String studentCol1 = studentPair.has("column_1") ? studentPair.get("column_1").asText() : "";
                    String studentCol2 = studentPair.has("column_2") ? studentPair.get("column_2").asText() : "";
                    String studentCol3 = studentPair.has("column_3") ? studentPair.get("column_3").asText() : "";

                    boolean matches = hasCol3 ?
                        (correctCol1.equalsIgnoreCase(studentCol1) && correctCol2.equalsIgnoreCase(studentCol2) && correctCol3.equalsIgnoreCase(studentCol3)) :
                        (correctCol1.equalsIgnoreCase(studentCol1) && correctCol2.equalsIgnoreCase(studentCol2));

                    if (matches) {
                        correctMatches++;
                        correctDetails.add(hasCol3 ?
                            String.format("Pair %d: %s - %s - %s", pairNum, correctCol1, correctCol2, correctCol3) :
                            String.format("Pair %d: %s - %s", pairNum, correctCol1, correctCol2));
                    } else {
                        incorrectDetails.add(hasCol3 ?
                            String.format("Pair %d: Expected '%s-%s-%s', Got '%s-%s-%s'", pairNum, correctCol1, correctCol2, correctCol3, studentCol1, studentCol2, studentCol3) :
                            String.format("Pair %d: Expected '%s-%s', Got '%s-%s'", pairNum, correctCol1, correctCol2, studentCol1, studentCol2));
                    }
                } else {
                    incorrectDetails.add(String.format("Pair %d: (no answer)", pairNum));
                }
            }

            double score = calculatePartialScore(correctMatches, correctPairsMap.size(), question.getPoints());
            String feedback = buildPartialFeedback(correctMatches, correctPairsMap.size(), score,
                question.getPoints(), correctDetails, incorrectDetails, null);
            setAnswerResult(answer, correctMatches == correctPairsMap.size(), score, question.getPoints(), feedback);

        } catch (Exception e) {
            handleScoringError(answer, question, "matching");
        }
    }

    private void scoreFillInBlankAnswer(Answer answer, Question question) {
        String correctAnswers = extractCorrectAnswersFromQuestion(question);
        if (correctAnswers == null || correctAnswers.trim().isEmpty()) return;

        String[] correctOptions = correctAnswers.split(",");
        List<String> studentAnswers = new ArrayList<>();

        try {
            JsonNode studentNode = objectMapper.readTree(answer.getContent().trim());
            if (studentNode.isObject()) {
                for (int i = 0; i < correctOptions.length; i++) {
                    JsonNode answerNode = studentNode.get(String.valueOf(i));
                    studentAnswers.add(answerNode != null && answerNode.isTextual() ? answerNode.asText().trim() : "");
                }
            } else {
                throw new Exception("Not JSON object");
            }
        } catch (Exception e) {
            for (String ans : answer.getContent().split(",")) {
                studentAnswers.add(ans.trim());
            }
        }

        while (studentAnswers.size() < correctOptions.length) {
            studentAnswers.add("");
        }

        int correctCount = 0;
        List<String> correctDetails = new ArrayList<>();
        List<String> incorrectDetails = new ArrayList<>();

        for (int i = 0; i < correctOptions.length; i++) {
            String correctOption = correctOptions[i].trim();
            String studentAnswer = studentAnswers.get(i);

            boolean isCorrect = correctOption.contains("|") ?
                java.util.Arrays.stream(correctOption.split("\\|")).anyMatch(opt -> studentAnswer.equalsIgnoreCase(opt.trim())) :
                studentAnswer.equalsIgnoreCase(correctOption);

            if (isCorrect) {
                correctCount++;
                correctDetails.add(String.format("Blank %d: %s", i + 1, studentAnswer));
            } else {
                incorrectDetails.add(studentAnswer.isEmpty() ?
                    String.format("Blank %d: (empty) - expected: %s", i + 1, correctOption.replace("|", " or ")) :
                    String.format("Blank %d: %s - expected: %s", i + 1, studentAnswer, correctOption.replace("|", " or ")));
            }
        }

        double score = calculatePartialScore(correctCount, correctOptions.length, question.getPoints());
        boolean fullyCorrect = correctCount == correctOptions.length;

        if (fullyCorrect) {
            setAnswerResult(answer, true, score, question.getPoints(), "Perfect! All answers are correct.");
        } else if (correctCount > 0) {
            String feedback = buildPartialFeedback(correctCount, correctOptions.length, score,
                question.getPoints(), correctDetails, incorrectDetails, null);
            setAnswerResult(answer, false, score, question.getPoints(), feedback);
        } else {
            setAnswerResult(answer, false, 0.0, question.getPoints(),
                "Incorrect. The correct answers are: " + correctAnswers.replace(",", ", ").replace("|", " or "));
        }
    }

    private void scoreRearrangeAnswer(Answer answer, Question question) {
        String correctOrder = extractCorrectAnswersFromQuestion(question);
        if (correctOrder == null || correctOrder.trim().isEmpty()) return;

        try {
            List<String> correctItems = java.util.Arrays.stream(correctOrder.split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toList());

            List<String> studentItems = new ArrayList<>();
            try {
                JsonNode studentArray = objectMapper.readTree(answer.getContent().trim());
                if (studentArray.isArray()) {
                    List<JsonNode> sortedItems = new ArrayList<>();
                    studentArray.forEach(sortedItems::add);
                    sortedItems.sort((a, b) -> Integer.compare(
                        a.has("position") ? a.get("position").asInt() : 0,
                        b.has("position") ? b.get("position").asInt() : 0));

                    sortedItems.forEach(item -> studentItems.add(
                        item.has("item") ? item.get("item").asText().trim() : ""));
                } else {
                    throw new Exception("Not JSON array");
                }
            } catch (Exception e) {
                for (String item : answer.getContent().split(",")) {
                    studentItems.add(item.trim());
                }
            }

            int correctCount = 0;
            List<String> correctDetails = new ArrayList<>();
            List<String> incorrectDetails = new ArrayList<>();

            for (int i = 0; i < Math.min(correctItems.size(), studentItems.size()); i++) {
                if (correctItems.get(i).equalsIgnoreCase(studentItems.get(i))) {
                    correctCount++;
                    correctDetails.add(String.format("Position %d: '%s'", i + 1, correctItems.get(i)));
                } else {
                    incorrectDetails.add(String.format("Position %d: Expected '%s', Got '%s'",
                        i + 1, correctItems.get(i), studentItems.get(i)));
                }
            }

            double score = calculatePartialScore(correctCount, correctItems.size(), question.getPoints());
            String feedback = buildPartialFeedback(correctCount, correctItems.size(), score,
                question.getPoints(), correctDetails, incorrectDetails, null);
            setAnswerResult(answer, correctCount == correctItems.size(), score, question.getPoints(), feedback);

        } catch (Exception e) {
            handleScoringError(answer, question, "rearrange");
        }
    }

    private void scoreSliderAnswer(Answer answer, Question question) {
        String correctValue = extractCorrectAnswersFromQuestion(question);
        if (correctValue == null || correctValue.trim().isEmpty()) {
            answer.setCorrect(false);
            answer.setScore(0.0);
            answer.setMaxScore(question.getPoints());
            answer.setFeedback("No correct answer configured for this slider question.");
            return;
        }

        try {
            double studentValue = Double.parseDouble(answer.getContent().trim());
            double correctValueDouble = Double.parseDouble(correctValue.trim());

            JsonNode configNode = Utils.Json.parseConfigurationData(question.getConfigurationData(), objectMapper);
            double minValue = configNode.has("minValue") ? configNode.get("minValue").asDouble() : 0;
            double maxValue = configNode.has("maxValue") ? configNode.get("maxValue").asDouble() : 100;
            double step = configNode.has("step") ? configNode.get("step").asDouble() : 1.0;
            String unit = configNode.has("unit") ? configNode.get("unit").asText() : "";

            double range = maxValue - minValue;
            double tolerance = Math.max(
                Math.max(correctValueDouble * 0.05, step),
                range * 0.02
            );

            if (studentValue < minValue || studentValue > maxValue) {
                answer.setCorrect(false);
                answer.setScore(0.0);
                answer.setMaxScore(question.getPoints());
                answer.setFeedback(String.format("Your answer %.2f%s is outside the valid range (%.2f - %.2f%s).",
                    studentValue, unit, minValue, maxValue, unit));
                return;
            }

            double difference = Math.abs(studentValue - correctValueDouble);
            boolean isCorrect = difference <= tolerance;

            double maxDifference = tolerance * 2;
            double partialScore = Math.max(0, (maxDifference - difference) / maxDifference) * question.getPoints();

            answer.setCorrect(isCorrect);
            answer.setScore(partialScore);
            answer.setMaxScore(question.getPoints());

            String feedback;
            if (isCorrect) {
                feedback = String.format("Correct! Your answer: %.2f%s, Correct answer: %.2f%s. Score: %.1f/%.1f",
                    studentValue, unit, correctValueDouble, unit, partialScore, question.getPoints());
            } else {
                feedback = String.format("Your answer: %.2f%s, Correct answer: %.2f%s (tolerance: ±%.2f%s). Score: %.1f/%.1f",
                    studentValue, unit, correctValueDouble, unit, tolerance, unit, partialScore, question.getPoints());
            }
            answer.setFeedback(feedback);

        } catch (NumberFormatException e) {
            answer.setCorrect(false);
            answer.setScore(0.0);
            answer.setMaxScore(question.getPoints());
            answer.setFeedback("Invalid number format. Please provide a numeric value.");
        } catch (Exception e) {
            log.error("Error processing slider answer: {}", e.getMessage());
            answer.setCorrect(false);
            answer.setScore(0.0);
            answer.setMaxScore(question.getPoints());
            answer.setFeedback("Error processing answer. Please contact your instructor.");
        }
    }

    private void scorePuzzleAnswer(Answer answer, Question question) {
        String correctOrder = extractCorrectAnswersFromQuestion(question);
        if (correctOrder == null || correctOrder.trim().isEmpty()) {
            setAnswerResult(answer, false, 0.0, question.getPoints(), "No correct puzzle configuration found.");
            return;
        }

        try {
            List<String> correctPieces = java.util.Arrays.stream(correctOrder.split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toList());
            List<String> studentPieces = java.util.Arrays.stream(answer.getContent().split(","))
                .map(String::trim).collect(java.util.stream.Collectors.toList());

            if (studentPieces.size() != correctPieces.size()) {
                setAnswerResult(answer, false, 0.0, question.getPoints(),
                    String.format("Incorrect number of pieces. Expected %d, got %d.", correctPieces.size(), studentPieces.size()));
                return;
            }

            int correctCount = 0;
            List<String> correctDetails = new ArrayList<>();
            List<String> incorrectDetails = new ArrayList<>();

            for (int i = 0; i < correctPieces.size(); i++) {
                if (correctPieces.get(i).equals(studentPieces.get(i))) {
                    correctCount++;
                    correctDetails.add(String.format("Position %d: %s", i + 1, correctPieces.get(i)));
                } else {
                    incorrectDetails.add(String.format("Position %d: Expected %s, got %s",
                        i + 1, correctPieces.get(i), studentPieces.get(i)));
                }
            }

            double score = calculatePartialScore(correctCount, correctPieces.size(), question.getPoints());
            String feedback = buildPartialFeedback(correctCount, correctPieces.size(), score,
                question.getPoints(), correctDetails, incorrectDetails, null);
            setAnswerResult(answer, correctCount == correctPieces.size(), score, question.getPoints(), feedback);

        } catch (Exception e) {
            handleScoringError(answer, question, "puzzle");
        }
    }

    // Score answer using AI, with code compilation for coding questions
    public void scoreAnswerWithAI(Answer answer, Question question) {
        Double compilerScore = null;
        Double compilerMaxScore = null;
        Boolean compilerIsCorrect = null;
        String compilerFeedback = null;

        try {

            // For coding questions, compile and test code first
            if (question.getType() == QuestionType.CODING) {
                String language = answer.getLanguage();
                String testInput = null;
                String expectedOutput = null;
                String codeContent = null;

                if (language == null) {
                    language = codeCompilationService.extractLanguageFromQuestion(question);
                    if (language != null) {
                        answer.setLanguage(language);
                        log.info("Extracted language '{}' from question configuration for question {}", language, question.getId());
                    }
                }

                codeContent = codeCompilationService.extractCodeContent(answer);
                if (codeContent == null || codeContent.trim().isEmpty()) {
                    log.warn("No code content found for coding question {}", question.getId());
                    answer.setScore(0.0);
                    answer.setMaxScore(question.getPoints());
                    answer.setCorrect(false);
                    answer.setFeedback("No code submitted. Please upload a code file or enter code in the text area.");
                    return;
                }

                Map<String, String> testCases = codeCompilationService.extractTestCasesFromQuestion(question);
                if (testCases != null) {
                    testInput = testCases.get("input");
                    expectedOutput = testCases.get("output");
                    log.info("Extracted test cases for question {}: input='{}', expected='{}'", question.getId(), testInput, expectedOutput);
                } else {
                    log.warn("No test cases found for question {}. Configuration data: {}", question.getId(), question.getConfigurationData());
                }

                if (language != null && codeContent != null) {
                    log.info("Starting code compilation for coding question {} with language: {} ({} lines of code)",
                        question.getId(), language, codeContent.split("\r?\n").length);
                    try {
                        CodeCompilationService.CodeCompilationResult compilationResult =
                            codeCompilationService.compileCode(codeContent, language, testInput, expectedOutput).get();

                        log.info("Code compilation result for question {}: success={}, feedback={}, method={}",
                            question.getId(), compilationResult.isCompilationSuccessful(), compilationResult.getFeedback(), compilationResult.getCompilationMethod());

                        if (!compilationResult.isCompilationSuccessful()) {
                            log.info("Code compilation failed for question {}, setting score to 0", question.getId());
                            answer.setScore(0.0);
                            answer.setMaxScore(question.getPoints());
                            answer.setCorrect(false);
                            answer.setFeedback("Code compilation failed: " + compilationResult.getFeedback());
                            return;
                        }

                        // Score based on output match if test cases provided
                        compilerMaxScore = question.getPoints();
                        if (testCases != null && testCases.containsKey("output")) {
                            String compilerExpectedOutput = testCases.get("output").trim();
                            String compilerActualOutput = compilationResult.getOutput() != null ? compilationResult.getOutput().trim() : "";

                            if (!compilerExpectedOutput.isEmpty() && compilerExpectedOutput.equals(compilerActualOutput)) {
                                compilerScore = question.getPoints();
                                compilerIsCorrect = true;
                            } else {
                                compilerScore = question.getPoints() * 0.6;
                                compilerIsCorrect = false;
                            }
                        } else {
                            compilerScore = question.getPoints() * 0.6;
                            compilerIsCorrect = false;
                        }

                        log.info("Code compiled successfully for question {}, proceeding to AI scoring", question.getId());

                        StringBuilder compilationFeedback = new StringBuilder();
                        compilationFeedback.append("Code Compilation Results: ").append(compilationResult.getFeedback());
                        compilationFeedback.append(" Method: ").append(compilationResult.getCompilationMethod());

                        if (compilationResult.getOutput() != null && !compilationResult.getOutput().trim().isEmpty()) {
                            compilationFeedback.append(" Output: ").append(compilationResult.getOutput().trim());
                        }

                        if (compilationResult.getExecutionTime() != null && !compilationResult.getExecutionTime().trim().isEmpty()) {
                            compilationFeedback.append(" Execution Time: ").append(compilationResult.getExecutionTime());
                        }

                        if (compilationResult.getMemoryUsage() != null && !compilationResult.getMemoryUsage().trim().isEmpty()) {
                            compilationFeedback.append(" Memory Usage: ").append(compilationResult.getMemoryUsage());
                        }

                        if (compilationResult.getErrorOutput() != null && !compilationResult.getErrorOutput().trim().isEmpty()) {
                            compilationFeedback.append(" Errors: ").append(compilationResult.getErrorOutput().trim());
                        }

                        compilerFeedback = compilationFeedback.toString();
                        answer.setFeedback(compilerFeedback);

                    } catch (Exception e) {
                        log.error("Code compilation exception for question {}: {}", question.getId(), e.getMessage(), e);
                        answer.setFeedback("Code compilation failed: " + e.getMessage() + ". ");
                    }
                } else {
                    log.warn("Coding question {} has no language specification in question or answer", question.getId());
                }
            }

            // Get AI evaluation
            AiCorrectionService.AiCorrectionResult result = aiCorrectionService.correctAnswer(question, answer).get();

            // Check if AI is available
            String aiFeedback = result.getFeedback() != null ? result.getFeedback() : "";
            boolean aiUnavailable = aiFeedback.contains("AI correction unavailable") ||
                                   aiFeedback.contains("unavailable") ||
                                   (result.getScore() == 0.0 && aiFeedback.contains("review manually"));

            // Fallback to compiler score if AI unavailable for coding questions
            if (question.getType() == QuestionType.CODING && compilerScore != null && aiUnavailable) {
                log.info("AI unavailable for coding question {}, using compiler results", question.getId());
                answer.setScore(compilerScore);
                answer.setMaxScore(compilerMaxScore);
                answer.setCorrect(compilerIsCorrect);
                answer.setFeedback(compilerFeedback + "\n\nAI evaluation temporarily unavailable. Score based on compilation results.");
            } else {
                answer.setScore(result.getScore());
                answer.setMaxScore(result.getMaxScore());
                answer.setCorrect(result.isCorrect());

                String currentFeedback = answer.getFeedback() != null ? answer.getFeedback() : "";

                if (!currentFeedback.isEmpty() && !aiFeedback.isEmpty()) {
                    answer.setFeedback(currentFeedback + "\n\nAI Evaluation: " + aiFeedback);
                } else if (!currentFeedback.isEmpty()) {
                    answer.setFeedback(currentFeedback);
                } else {
                    answer.setFeedback(aiFeedback);
                }
            }

        } catch (Exception e) {
            if (question.getType() == QuestionType.CODING && compilerScore != null) {
                log.info("AI scoring failed for coding question {}, using compiler results", question.getId());
                answer.setScore(compilerScore);
                answer.setMaxScore(compilerMaxScore);
                answer.setCorrect(compilerIsCorrect);
                answer.setFeedback(compilerFeedback + "\n\nAI evaluation temporarily unavailable. Score based on compilation results.");
            } else {
                answer.setScore(0.0);
                answer.setMaxScore(question.getPoints());
                answer.setCorrect(false);
                answer.setFeedback("AI scoring failed: " + e.getMessage());
                throw new RuntimeException("AI scoring failed", e);
            }
        }
    }

    // Extract correct answers from question configuration based on question type
    private String extractCorrectAnswersFromQuestion(Question question) {
        if (question.getConfigurationData() == null || question.getConfigurationData().isEmpty()) return null;

        try {
            JsonNode config = Utils.Json.parseConfigurationData(question.getConfigurationData(), objectMapper);

            switch (question.getType()) {
                case MCQ:
                    JsonNode options = config.get("options");
                    if (options != null && options.isObject()) {
                        List<String> correct = new ArrayList<>();
                        options.fields().forEachRemaining(entry -> {
                            JsonNode opt = entry.getValue();
                            if (opt.has("correct") && opt.get("correct").asBoolean()) {
                                correct.add(opt.get("text").asText());
                            }
                        });
                        return correct.isEmpty() ? null : String.join(",", correct);
                    }
                    break;

                case TRUE_FALSE:
                    JsonNode tfNode = config.get("correctAnswer");
                    return tfNode != null ? tfNode.asText() : null;

                case SLIDER:
                    JsonNode sliderNode = config.get("correctValue");
                    return sliderNode != null ? sliderNode.asText() : null;

                case FILL_IN_BLANK:
                    JsonNode blanks = config.get("blanks");
                    if (blanks != null && blanks.isArray()) {
                        List<JsonNode> sorted = new ArrayList<>();
                        blanks.forEach(sorted::add);
                        sorted.sort((a, b) -> Integer.compare(
                            a.has("index") ? a.get("index").asInt() : 0,
                            b.has("index") ? b.get("index").asInt() : 0));

                        List<String> results = new ArrayList<>();
                        for (JsonNode blank : sorted) {
                            JsonNode answers = blank.get("correctAnswers");
                            if (answers != null && answers.isArray() && answers.size() > 0) {
                                results.add(joinJsonArray(answers, "|"));
                            }
                        }
                        return results.isEmpty() ? null : String.join(",", results);
                    }
                    break;

                case REARRANGE:
                    JsonNode orderNode = config.get("correctOrder");
                    return (orderNode != null && orderNode.isArray()) ? joinJsonArray(orderNode, ",") : null;

                case PUZZLE:
                    JsonNode puzzleNode = config.get("correctAnswer");
                    return (puzzleNode != null && puzzleNode.isArray()) ? joinJsonArray(puzzleNode, ",") : null;

                case SELECT_ON_PHOTO:
                    JsonNode blocksNode = config.get("selectedBlocks");
                    return (blocksNode != null && blocksNode.isArray()) ? joinJsonArray(blocksNode, ",") : null;

                default:
                    break;
            }

        } catch (Exception e) {
            log.error("Error parsing question configuration: {}", e.getMessage());
        }

        return null;
    }

    private String joinJsonArray(JsonNode arrayNode, String delimiter) {
        List<String> items = new ArrayList<>();
        arrayNode.forEach(item -> items.add(item.asText()));
        return String.join(delimiter, items);
    }

    private void scoreSelectOnPhotoAnswer(Answer answer, Question question) {
        try {
            JsonNode configNode = parseConfig(question);
            JsonNode correctBlocksNode = configNode.get("selectedBlocks");
            if (correctBlocksNode == null || !correctBlocksNode.isArray()) return;

            List<String> correctBlocks = new ArrayList<>();
            correctBlocksNode.forEach(block -> correctBlocks.add(block.asText()));

            List<String> studentBlocks = new ArrayList<>();
            try {
                JsonNode studentNode = objectMapper.readTree(answer.getContent().trim());
                if (studentNode.isArray()) {
                    studentNode.forEach(block -> studentBlocks.add(block.asText()));
                }
            } catch (Exception e) {
                setAnswerResult(answer, false, 0.0, question.getPoints(), "Invalid answer format");
                return;
            }

            int correctCount = 0;
            List<String> correctSelections = new ArrayList<>();
            List<String> incorrectSelections = new ArrayList<>();
            List<String> missedSelections = new ArrayList<>();

            for (String studentBlock : studentBlocks) {
                if (correctBlocks.contains(studentBlock)) {
                    correctCount++;
                    correctSelections.add(studentBlock);
                } else {
                    incorrectSelections.add(studentBlock);
                }
            }

            for (String correctBlock : correctBlocks) {
                if (!studentBlocks.contains(correctBlock)) {
                    missedSelections.add(correctBlock);
                }
            }

            double score;
            boolean isCorrect = correctCount == correctBlocks.size() && incorrectSelections.isEmpty();

            if (isCorrect) {
                score = question.getPoints();
                setAnswerResult(answer, true, score, question.getPoints(), "Perfect! All blocks selected correctly.");
            } else if (correctCount > 0) {
                double ratio = Math.max(0, (double)(correctCount - incorrectSelections.size()) / correctBlocks.size());
                score = ratio * question.getPoints();
                String feedback = buildPartialFeedback(correctCount, correctBlocks.size(), score,
                    question.getPoints(), correctSelections, incorrectSelections, missedSelections);
                setAnswerResult(answer, false, score, question.getPoints(), feedback);
            } else {
                setAnswerResult(answer, false, 0.0, question.getPoints(), "No correct blocks selected.");
            }

        } catch (Exception e) {
            handleScoringError(answer, question, "SELECT_ON_PHOTO");
        }
    }

}
