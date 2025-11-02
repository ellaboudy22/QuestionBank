package com.questionbank.QuestionBank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questionbank.QuestionBank.dto.AnswerDTO;
import com.questionbank.QuestionBank.entity.Answer;
import com.questionbank.QuestionBank.entity.AnswerType;
import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.entity.QuestionType;
import com.questionbank.QuestionBank.exception.Validation;
import com.questionbank.QuestionBank.repository.AnswerRepository;
import com.questionbank.QuestionBank.repository.QuestionRepository;
import com.questionbank.QuestionBank.service.plagiarism.embedding.ImageEmbedder;
import com.questionbank.QuestionBank.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Service for managing answer submissions with auto-grading and plagiarism detection
@Service
@Transactional
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final MediaService mediaService;
    private final CorrectionService correctionService;
    private final ImageEmbedder imageEmbeddingService;
    private final PlagiarismService plagiarismService;

    @Autowired
    public AnswerService(AnswerRepository answerRepository,
                        QuestionRepository questionRepository,
                        ObjectMapper objectMapper,
                        MediaService mediaService,
                        CorrectionService correctionService,
                        ImageEmbedder imageEmbeddingService,
                        PlagiarismService plagiarismService) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.mediaService = mediaService;
        this.correctionService = correctionService;
        this.imageEmbeddingService = imageEmbeddingService;
        this.plagiarismService = plagiarismService;
    }

    public AnswerDTO createAnswer(AnswerDTO request) {
        return createAnswer(request, null);
    }

    public AnswerDTO createAnswer(AnswerDTO request, List<MultipartFile> files) {
        if (request.getQuestionId() == null) {
            throw new Validation.ValidationException("questionId cannot be null");
        }
        if (request.getType() == null) {
            throw new Validation.ValidationException("answerType cannot be null");
        }
        Validation.notNullOrEmpty(request.getContent(), "content");

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new Validation.ResourceNotFoundException("Question", request.getQuestionId().toString()));

        validateAnswerType(question.getType(), request.getType());

        Validation.maxLength(request.getContent(), "content", 10000);

        Answer answer = new Answer(
                request.getQuestionId(),
                request.getType(),
                request.getContent(),
                request.getMediaFiles() != null ?
                    request.getMediaFiles().stream().map(MultipartFile::getOriginalFilename).collect(Collectors.toList()).toString() : Utils.Constants.EMPTY_JSON_ARRAY,
                request.getLanguage(),
                false,
                0.0,
                0.0,
                "",
                request.getSubmittedBy(),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        Answer savedAnswer = answerRepository.save(answer);

        if (hasTextContent(request.getType())) {
            performTextPlagiarismCheck(savedAnswer);
        }

        if (files != null && !files.isEmpty()) {
            try {
                List<String> filePaths = mediaService.saveMedia(files, savedAnswer.getId(), "Answers", request.getSubmittedBy(), null);

                if (!filePaths.isEmpty()) {
                    savedAnswer.setMediaFilePaths(objectMapper.writeValueAsString(filePaths));
                    savedAnswer = answerRepository.save(savedAnswer);

                    performMediaPlagiarismCheck(savedAnswer, files, filePaths);
                }
            } catch (Exception e) {
                log.error("Failed to save media files: {}", e.getMessage());
            }
        }

        // Auto-score for preset answer types, use AI for essay/coding questions
        if (question.getType().requiresPresetAnswers()) {
            correctionService.autoScoreAnswer(savedAnswer, question);
        } else {
            try {
                correctionService.scoreAnswerWithAI(savedAnswer, question);
            } catch (Exception e) {
                String answerId = savedAnswer.getId() != null ? savedAnswer.getId().toString() : "pending";
                log.error("AI scoring failed for answer {}: {}", answerId, e.getMessage());
                savedAnswer.setScore(0.0);
                savedAnswer.setMaxScore(question.getPoints());
                savedAnswer.setCorrect(false);
                savedAnswer.setFeedback("AI scoring temporarily unavailable. Please contact your instructor.");
            }
        }

        return AnswerDTO.from(savedAnswer);
    }

    @Transactional(readOnly = true)
    public AnswerDTO getAnswerById(UUID id) {
        Answer answer = answerRepository.findById(id)
                .orElseThrow(() -> new Validation.ResourceNotFoundException("Answer", id.toString()));
        return AnswerDTO.from(answer);
    }

    @Transactional(readOnly = true)
    public List<AnswerDTO> getAnswersByQuestionId(UUID questionId) {

        if (!questionRepository.existsById(questionId)) {
            throw new Validation.ResourceNotFoundException("Question", questionId.toString());
        }

        List<Answer> answers = answerRepository.findByQuestionIdAndIsActiveTrue(questionId);
        return answers.stream()
                .map(AnswerDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AnswerDTO> getAnswersByQuestionId(UUID questionId, Pageable pageable, String filter) {
        if (!questionRepository.existsById(questionId)) {
            throw new Validation.ResourceNotFoundException("Question", questionId.toString());
        }

        Page<Answer> answers;
        if ("correct".equalsIgnoreCase(filter)) {
            answers = answerRepository.findByQuestionIdAndIsCorrectTrueAndIsActiveTrue(questionId, pageable);
        } else if ("incorrect".equalsIgnoreCase(filter)) {
            answers = answerRepository.findByQuestionIdAndIsCorrectFalseAndIsActiveTrue(questionId, pageable);
        } else {
            answers = answerRepository.findByQuestionIdAndIsActiveTrue(questionId, pageable);
        }

        return answers.map(AnswerDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<AnswerDTO> getAllAnswers(Pageable pageable, String filter) {
        Page<Answer> answers;

        if ("correct".equalsIgnoreCase(filter)) {
            answers = answerRepository.findByIsCorrectTrue(pageable);
        } else if ("incorrect".equalsIgnoreCase(filter)) {
            answers = answerRepository.findByIsCorrectFalse(pageable);
        } else if ("active".equalsIgnoreCase(filter)) {
            answers = answerRepository.findByIsActiveTrue(pageable);
        } else {
            answers = answerRepository.findAll(pageable);
        }

        return answers.map(AnswerDTO::from);
    }

    public AnswerDTO updateAnswer(UUID id, AnswerDTO request) {
        Answer answer = answerRepository.findById(id)
                .orElseThrow(() -> new Validation.ResourceNotFoundException("Answer", id.toString()));

        if (request.getContent() != null) {
            Validation.maxLength(request.getContent(), "content", 10000);
            answer.setContent(request.getContent());
        }

        if (request.getLanguage() != null) {
            answer.setLanguage(request.getLanguage());
        }

        if (request.isCorrect() != null) {
            answer.setCorrect(request.isCorrect());
        }

        if (request.getScore() != null) {
            answer.setScore(request.getScore());
        }

        if (request.getMaxScore() != null) {
            answer.setMaxScore(request.getMaxScore());
        }

        if (request.getFeedback() != null) {
            answer.setFeedback(request.getFeedback());
        }

        if (request.isActive() != null) {
            answer.setActive(request.isActive());
        }

        Answer updatedAnswer = answerRepository.save(answer);
        return AnswerDTO.from(updatedAnswer);
    }

    public void deleteAnswer(UUID id) {
        Answer answer = answerRepository.findById(id)
                .orElseThrow(() -> new Validation.ResourceNotFoundException("Answer", id.toString()));

        answer.setActive(false);
        answerRepository.save(answer);
    }

    public void hardDeleteAnswer(UUID id) {
        if (!answerRepository.existsById(id)) {
            throw new Validation.ResourceNotFoundException("Answer", id.toString());
        }
        answerRepository.deleteById(id);
    }

    private void validateAnswerType(QuestionType questionType, AnswerType answerType) {
        switch (questionType) {
            case MCQ:
                if (answerType != AnswerType.MULTIPLE_CHOICE) {
                    throw new Validation.ValidationException("MCQ questions require multiple choice answers");
                }
                break;

            case TRUE_FALSE:
                if (answerType != AnswerType.TRUE_FALSE) {
                    throw new Validation.ValidationException("True/False questions require true/false answer type");
                }
                break;

            case ESSAY_SHORT:
                if (answerType != AnswerType.SHORT_ANSWER) {
                    throw new Validation.ValidationException("Short essay questions require short answer type");
                }
                break;

            case ESSAY_LONG:
                if (answerType != AnswerType.LONG_ANSWER) {
                    throw new Validation.ValidationException("Long essay questions require long answer type");
                }
                break;

            case FILL_IN_BLANK:
                if (answerType != AnswerType.FILL_IN_BLANK) {
                    throw new Validation.ValidationException("Fill in blank questions require fill in blank answer type");
                }
                break;

            case CODING:
                if (!answerType.isCodeSubmission()) {
                    throw new Validation.ValidationException("Coding questions require code submission answers");
                }
                break;

            case MATCHING:
                if (answerType != AnswerType.MATCHING) {
                    throw new Validation.ValidationException("Matching questions require matching answer type");
                }
                break;

            case REARRANGE:
                if (answerType != AnswerType.REARRANGE) {
                    throw new Validation.ValidationException("Rearrange questions require rearrange answer type");
                }
                break;

            case SLIDER:
                if (answerType != AnswerType.SLIDER) {
                    throw new Validation.ValidationException("Slider questions require slider answer type");
                }
                break;

            case SELECT_ON_PHOTO:
                if (answerType != AnswerType.MULTIPLE_CHOICE) {
                    throw new Validation.ValidationException("Select on photo questions require multiple choice answer type (JSON array of selected blocks)");
                }
                break;

            case PUZZLE:
                if (answerType != AnswerType.PUZZLE) {
                    throw new Validation.ValidationException("Puzzle questions require puzzle answer type");
                }
                break;

            default:
                log.warn("Unknown question type: {}. Allowing answer type: {}", questionType, answerType);
                break;
        }
    }

    private boolean hasTextContent(AnswerType answerType) {
        return answerType == AnswerType.SHORT_ANSWER ||
               answerType == AnswerType.LONG_ANSWER ||
               answerType == AnswerType.CODE_SUBMISSION;
    }

    private void performTextPlagiarismCheck(Answer answer) {
        try {
            if (answer.getContent() == null || answer.getContent().trim().isEmpty()) {
                return;
            }

            PlagiarismService.PlagiarismResult result =
                plagiarismService.detectTextPlagiarism(
                    answer.getContent(),
                    answer.getQuestionId(),
                    answer.getId());

            if (result.isPlagiarized()) {
                answer.setPlagiarismScore(result.getSimilarityScore());
                answer.setPlagiarized(true);

                try {
                    answer.setPlagiarismDetails(objectMapper.writeValueAsString(result.getDetails()));
                } catch (Exception e) {
                    log.error("Failed to serialize plagiarism details: {}", e.getMessage());
                    answer.setPlagiarismDetails(Utils.Constants.EMPTY_JSON_OBJECT);
                }

                answerRepository.save(answer);

                log.warn("Text plagiarism detected for answer {} with score {}",
                    answer.getId(), result.getSimilarityScore());
            }

        } catch (Exception e) {
            log.error("Error during text plagiarism check for answer {}: {}", answer.getId(), e.getMessage());
        }
    }

    // Check for plagiarism in uploaded media files by extracting and comparing embeddings
    private void performMediaPlagiarismCheck(Answer answer, List<MultipartFile> files, List<String> filePaths) {
        try {
            double[] combinedEmbeddings = null;
            PlagiarismService.PlagiarismResult plagiarismResult = null;

            // Process each file type differently
            for (MultipartFile file : files) {
                if (Utils.Files.isImageFile(file)) {
                    try {
                        byte[] imageBytes = file.getBytes();
                        double[] embeddings = imageEmbeddingService.extractImageFeatures(imageBytes);

                        if (combinedEmbeddings == null) {
                            combinedEmbeddings = embeddings;
                        } else {
                            combinedEmbeddings = combineEmbeddings(combinedEmbeddings, embeddings);
                        }

                        PlagiarismService.PlagiarismResult currentResult =
                            plagiarismService.detectImagePlagiarism(
                                imageBytes, answer.getQuestionId(), answer.getId());

                        if (plagiarismResult == null || currentResult.getSimilarityScore() > plagiarismResult.getSimilarityScore()) {
                            plagiarismResult = currentResult;
                        }

                    } catch (Exception e) {
                        log.error("Failed to process image file {}: {}", file.getOriginalFilename(), e.getMessage());
                    }
                } else if (Utils.Files.isVideoFile(file)) {
                    try {

                        List<byte[]> frames = mediaService.extractVideoFrames(file, true, true);
                        for (byte[] frameBytes : frames) {
                            double[] embeddings = imageEmbeddingService.extractImageFeatures(frameBytes);

                            if (combinedEmbeddings == null) {
                                combinedEmbeddings = embeddings;
                            } else {
                                combinedEmbeddings = combineEmbeddings(combinedEmbeddings, embeddings);
                            }

                            PlagiarismService.PlagiarismResult currentResult =
                                plagiarismService.detectImagePlagiarism(
                                    frameBytes, answer.getQuestionId(), answer.getId());

                            if (plagiarismResult == null || currentResult.getSimilarityScore() > plagiarismResult.getSimilarityScore()) {
                                plagiarismResult = currentResult;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to process video file {}: {}", file.getOriginalFilename(), e.getMessage());
                    }
                } else if (Utils.Files.isPdfFile(file)) {
                    try {

                        List<byte[]> pages = mediaService.extractPdfPages(file, true, true);
                        for (byte[] pageBytes : pages) {
                            double[] embeddings = imageEmbeddingService.extractImageFeatures(pageBytes);

                            if (combinedEmbeddings == null) {
                                combinedEmbeddings = embeddings;
                            } else {
                                combinedEmbeddings = combineEmbeddings(combinedEmbeddings, embeddings);
                            }

                            PlagiarismService.PlagiarismResult currentResult =
                                plagiarismService.detectImagePlagiarism(
                                    pageBytes, answer.getQuestionId(), answer.getId());

                            if (plagiarismResult == null || currentResult.getSimilarityScore() > plagiarismResult.getSimilarityScore()) {
                                plagiarismResult = currentResult;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to process PDF file {}: {}", file.getOriginalFilename(), e.getMessage());
                    }
                } else if (Utils.Files.isDocxFile(file)) {
                    try {
                        String docxText = mediaService.extractDocxText(file);
                        if (docxText != null && !docxText.trim().isEmpty()) {
                            String combinedContent = answer.getContent() + "\n\n[DOCX Content]\n" + docxText;

                            PlagiarismService.PlagiarismResult textResult =
                                plagiarismService.detectTextPlagiarism(
                                    combinedContent,
                                    answer.getQuestionId(),
                                    answer.getId());

                            if (textResult != null && (plagiarismResult == null || textResult.getSimilarityScore() > plagiarismResult.getSimilarityScore())) {
                                plagiarismResult = textResult;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to process DOCX file {}: {}", file.getOriginalFilename(), e.getMessage());
                    }
                }
            }

            if (combinedEmbeddings != null) {
                String embeddingsJson = imageEmbeddingService.serializeEmbeddings(combinedEmbeddings);
                answer.setImageEmbeddings(embeddingsJson);

                log.info("Saving embeddings for answer {} (dimensions: {})",
                           answer.getId(), combinedEmbeddings.length);

                if (plagiarismResult != null) {
                    answer.setPlagiarismScore(plagiarismResult.getSimilarityScore());
                    answer.setPlagiarized(plagiarismResult.isPlagiarized());

                    log.info("Answer {} plagiarism result: score={}, isPlagiarized={}",
                               answer.getId(), plagiarismResult.getSimilarityScore(),
                               plagiarismResult.isPlagiarized());

                    try {
                        answer.setPlagiarismDetails(objectMapper.writeValueAsString(plagiarismResult.getDetails()));
                    } catch (Exception e) {
                        log.error("Failed to serialize plagiarism details: {}", e.getMessage());
                        answer.setPlagiarismDetails(Utils.Constants.EMPTY_JSON_OBJECT);
                    }

                    if (plagiarismResult.isPlagiarized()) {
                        log.warn("Plagiarism detected for answer {} with score {}",
                            answer.getId(), plagiarismResult.getSimilarityScore());
                    }
                } else {

                    log.info("No plagiarism detected for answer {} (first submission or no matches)",
                               answer.getId());
                    answer.setPlagiarismScore(0.0);
                    answer.setPlagiarized(false);
                    answer.setPlagiarismDetails(Utils.Constants.EMPTY_JSON_OBJECT);
                }

                Answer updatedAnswer = answerRepository.save(answer);
                answerRepository.flush();
                log.info("Answer {} saved and flushed with embeddings to database", answer.getId());
            } else if (plagiarismResult != null) {

                answer.setPlagiarismScore(plagiarismResult.getSimilarityScore());
                answer.setPlagiarized(plagiarismResult.isPlagiarized());

                try {
                    answer.setPlagiarismDetails(objectMapper.writeValueAsString(plagiarismResult.getDetails()));
                } catch (Exception e) {
                    log.error("Failed to serialize plagiarism details: {}", e.getMessage());
                    answer.setPlagiarismDetails(Utils.Constants.EMPTY_JSON_OBJECT);
                }

                answerRepository.save(answer);

                if (plagiarismResult.isPlagiarized()) {
                    log.warn("Text plagiarism detected for answer {} with score {}",
                        answer.getId(), plagiarismResult.getSimilarityScore());
                }
            }

        } catch (Exception e) {
            log.error("Error during plagiarism check for answer {}: {}", answer.getId(), e.getMessage());
        }
    }

    // Average two embedding vectors element-wise
    private double[] combineEmbeddings(double[] embedding1, double[] embedding2) {
        if (embedding1.length != embedding2.length) {
            log.warn("Embedding dimensions mismatch: {} vs {}", embedding1.length, embedding2.length);
            return embedding1;
        }

        double[] combined = new double[embedding1.length];
        for (int i = 0; i < embedding1.length; i++) {
            combined[i] = (embedding1[i] + embedding2[i]) / 2.0;
        }
        return combined;
    }

}
