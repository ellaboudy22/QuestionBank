package com.questionbank.QuestionBank.service;

import com.questionbank.QuestionBank.entity.Answer;
import com.questionbank.QuestionBank.entity.Plagiarism;
import com.questionbank.QuestionBank.repository.AnswerRepository;
import com.questionbank.QuestionBank.repository.PlagiarismRepository;
import com.questionbank.QuestionBank.exception.Validation;
import com.questionbank.QuestionBank.service.plagiarism.ImageEmbedder;
import com.questionbank.QuestionBank.service.plagiarism.TextEmbedder;
import com.questionbank.QuestionBank.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// Handles plagiarism detection using ONNX embeddings for text and images
@Service
public class PlagiarismService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismService.class);

    private final PlagiarismRepository repository;
    private final AnswerRepository answerRepository;
    private final ImageEmbedder imageEmbedder;
    private final TextEmbedder textEmbedder;

    @Value("${plagiarism.text.threshold:0.8}")
    private double textThreshold;

    @Value("${plagiarism.image.threshold:0.85}")
    private double imageThreshold;

    @Autowired
    public PlagiarismService(PlagiarismRepository repository,
                           AnswerRepository answerRepository,
                           ImageEmbedder imageEmbedder,
                           TextEmbedder textEmbedder) {
        this.repository = repository;
        this.answerRepository = answerRepository;
        this.imageEmbedder = imageEmbedder;
        this.textEmbedder = textEmbedder;
    }

    public Plagiarism save(String path, String type, String user, UUID questionId, UUID answerId) {
        Validation.notNullOrEmpty(path, "Path");
        Validation.notNullOrEmpty(type, "Type");
        Validation.notNullOrEmpty(user, "User");
        if (questionId == null) {
            throw new Validation.ValidationException("Question ID is required");
        }
        if (answerId == null) {
            throw new Validation.ValidationException("Answer ID is required");
        }

        Plagiarism plagiarism = new Plagiarism(path, type, user, questionId, answerId);
        return repository.save(plagiarism);
    }

    public PlagiarismResult detectTextPlagiarism(String content, UUID questionId, UUID currentAnswerId) {
        try {
            List<Answer> existingAnswers = answerRepository.findByQuestionIdAndIsActiveTrue(questionId)
                .stream()
                .filter(answer -> !answer.getId().equals(currentAnswerId))
                .toList();

            log.debug("Checking text plagiarism for answer {} against {} existing answers",
                        currentAnswerId, existingAnswers.size());

            return performBruteForceDetection(
                existingAnswers,
                currentAnswerId,
                textThreshold,
                "text",
                answer -> {
                    if (answer.getContent() == null || answer.getContent().trim().isEmpty()) return null;
                    return calculateTextSimilarity(content, answer.getContent());
                }
            );
        } catch (Exception e) {
            log.error("Error during text plagiarism detection: {}", e.getMessage());
            return createErrorResult("Failed to analyze text for plagiarism: " + e.getMessage(), "text");
        }
    }

    public PlagiarismResult detectImagePlagiarism(byte[] imageData, UUID questionId, UUID currentAnswerId) {
        try {
            double[] currentEmbeddings = imageEmbedder.extractImageFeatures(imageData);
            List<Answer> existingAnswers = answerRepository.findByQuestionIdAndIsActiveTrue(questionId)
                .stream()
                .filter(answer -> !answer.getId().equals(currentAnswerId))
                .toList();

            log.debug("Checking image plagiarism for answer {} against {} existing answers",
                        currentAnswerId, existingAnswers.size());

            return performBruteForceDetection(
                existingAnswers,
                currentAnswerId,
                imageThreshold,
                "image",
                answer -> {
                    if (answer.getImageEmbeddings() == null || answer.getImageEmbeddings().isEmpty()) return null;
                    double[] existingEmbeddings = imageEmbedder.deserializeEmbeddings(answer.getImageEmbeddings());
                    if (existingEmbeddings.length == 0) return null;
                    return imageEmbedder.calculateCosineSimilarity(currentEmbeddings, existingEmbeddings);
                }
            );
        } catch (Exception e) {
            log.error("Error during image plagiarism detection from bytes: {}", e.getMessage());
            return createErrorResult("Failed to analyze image for plagiarism: " + e.getMessage(), "image");
        }
    }

    @FunctionalInterface
    private interface SimilarityCalculator {
        Double calculate(Answer answer);
    }

    // Compares current answer against all existing answers using provided similarity calculator
    private PlagiarismResult performBruteForceDetection(
            List<Answer> existingAnswers,
            UUID currentAnswerId,
            double threshold,
            String detectionType,
            SimilarityCalculator calculator) {

        List<Match> matches = new ArrayList<>();
        double maxSimilarity = 0.0;
        boolean isPlagiarized = false;

        log.debug("Checking {} plagiarism for answer {} against {} existing answers",
                    detectionType, currentAnswerId, existingAnswers.size());

        for (Answer existingAnswer : existingAnswers) {
            Double similarity = calculator.calculate(existingAnswer);

            if (similarity == null) continue;

            log.debug("Similarity with answer {} (by {}): {}",
                        existingAnswer.getId(), existingAnswer.getSubmittedBy(), similarity);

            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
            }

            if (similarity >= threshold) {
                isPlagiarized = true;
                matches.add(new Match(
                    existingAnswer.getId(),
                    existingAnswer.getSubmittedBy(),
                    similarity,
                    existingAnswer.getCreatedAt().toString()
                ));
                log.warn("PLAGIARISM DETECTED: {} similarity with answer {} (threshold: {})",
                           similarity, existingAnswer.getId(), threshold);
            }
        }

        log.info("{} plagiarism check complete: maxSimilarity={}, isPlagiarized={}, matches={}",
                   detectionType, maxSimilarity, isPlagiarized, matches.size());

        Map<String, Object> details = new HashMap<>();
        details.put("type", detectionType);
        details.put("method", detectionType.equals("image") ? "onnx-image-embedding" : "onnx-semantic");
        details.put("maxSimilarity", maxSimilarity);
        details.put("threshold", threshold);
        details.put("matchCount", matches.size());
        details.put("matches", matches);
        details.put("analysisTimestamp", System.currentTimeMillis());

        return new PlagiarismResult(maxSimilarity, isPlagiarized, details);
    }

    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }

        String normalized1 = Utils.Text.normalize(text1);
        String normalized2 = Utils.Text.normalize(text2);

        double[] embedding1 = textEmbedder.extractTextEmbedding(normalized1);
        double[] embedding2 = textEmbedder.extractTextEmbedding(normalized2);

        double semanticSim = textEmbedder.calculateSimilarity(embedding1, embedding2);

        // For short texts, blend semantic and character-level similarity
        if (normalized1.length() < 200 || normalized2.length() < 200) {
            double charSim = Utils.Math.characterSimilarity(normalized1, normalized2);
            return 0.7 * semanticSim + 0.3 * charSim;
        }

        return semanticSim;
    }

    private PlagiarismResult createErrorResult(String errorMessage, String type) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("error", errorMessage);
        errorDetails.put("type", type);
        return new PlagiarismResult(0.0, false, errorDetails);
    }

    public enum DetectionStrategy {
        TEXT_SIMILARITY,
        IMAGE_SIMILARITY,
        CODE_SIMILARITY
    }

    public static class Match {
        private final UUID answerId;
        private final String submittedBy;
        private final double similarity;
        private final String submittedAt;

        public Match(UUID answerId, String submittedBy, double similarity, String submittedAt) {
            this.answerId = answerId;
            this.submittedBy = submittedBy;
            this.similarity = similarity;
            this.submittedAt = submittedAt;
        }

        public UUID getAnswerId() { return answerId; }
        public String getSubmittedBy() { return submittedBy; }
        public double getSimilarity() { return similarity; }
        public String getSubmittedAt() { return submittedAt; }
    }

    public static class PlagiarismResult {
        private final double similarityScore;
        private final boolean isPlagiarized;
        private final Map<String, Object> details;

        public PlagiarismResult(double similarityScore, boolean isPlagiarized, Map<String, Object> details) {
            this.similarityScore = similarityScore;
            this.isPlagiarized = isPlagiarized;
            this.details = details;
        }

        public double getSimilarityScore() { return similarityScore; }
        public boolean isPlagiarized() { return isPlagiarized; }
        public Map<String, Object> getDetails() { return details; }
    }
}
