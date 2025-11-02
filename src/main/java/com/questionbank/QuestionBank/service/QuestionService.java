package com.questionbank.QuestionBank.service;

import com.questionbank.QuestionBank.dto.QuestionDTO;
import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.entity.QuestionType;
import com.questionbank.QuestionBank.exception.Validation;
import com.questionbank.QuestionBank.repository.QuestionRepository;
import com.questionbank.QuestionBank.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Service for managing questions with media handling and validation
@Service
@Transactional
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final QuestionRepository questionRepository;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    @Autowired
    public QuestionService(QuestionRepository questionRepository, MediaService mediaService,
                          ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    public QuestionDTO createQuestion(QuestionDTO request, String currentUser) {
        Validation.notNullOrEmpty(request.getTitle(), "title");
        Validation.minLength(request.getTitle(), "title", 5);
        Validation.maxLength(request.getTitle(), "title", 200);
        Validation.notNullOrEmpty(request.getContent(), "content");
        Validation.notNullOrEmpty(request.getModule(), "module");
        Validation.notNullOrEmpty(request.getUnit(), "unit");
        Validation.validateConfigurationData(request.getConfigurationData());

        String enrichedConfigData = enrichConfigurationWithMetadata(request.getConfigurationData(), request.getType());
        request.setConfigurationData(enrichedConfigData);

        Validation.validateQuestionType(request.getType().toString());
        Validation.validateQuestionContent(request.getContent(), request.getType());
        Validation.validateQuestionConfiguration(request.getConfigurationData(), request.getType());
        Validation.validateMediaFilePaths(request.getMediaFilePaths());
        Validation.validateAllowedMediaTypes(request.getAllowedMediaTypes());
        Validation.inRange(request.getDifficultyLevel(), "difficultyLevel", 1, 10);
        Validation.positive(request.getPoints(), "points");
        Validation.positive(request.getTimeLimitMinutes(), "timeLimitMinutes");
        Question question = new Question(
            request.getTitle(),
            request.getType(),
            request.getModule(),
            request.getUnit(),
            request.getContent(),
            Utils.Constants.EMPTY_JSON_ARRAY,
            request.getAllowMediaInsertion(),
            request.getAllowedMediaTypes(),
            request.getDifficultyLevel(),
            request.getPoints(),
            request.getTimeLimitMinutes(),
            request.getConfigurationData(),
            true,
            currentUser,
            currentUser
        );

        if (request.getType() == QuestionType.SELECT_ON_PHOTO) {
            if (request.getMediaFiles() == null || request.getMediaFiles().isEmpty()) {
                throw new Validation.ValidationException("SELECT_ON_PHOTO questions must have an image attached");
            }
        }

        Question savedQuestion = questionRepository.save(question);

        // Special processing for puzzle questions: split image into pieces
        if (request.getType() == QuestionType.PUZZLE && request.getMediaFiles() != null && !request.getMediaFiles().isEmpty()) {
            try {
                JsonNode configNode = Utils.Json.parseConfigurationData(request.getConfigurationData(), objectMapper);
                int gridRows = configNode.get("gridRows").asInt();
                int gridCols = configNode.get("gridCols").asInt();

                var puzzleResult = mediaService.processPuzzleImage(
                    request.getMediaFiles().get(0), gridRows, gridCols, savedQuestion.getId());

                if (puzzleResult.isSuccess()) {
                    JsonNode originalConfig = Utils.Json.parseConfigurationData(request.getConfigurationData(), objectMapper);
                    ObjectNode updatedConfig = objectMapper.createObjectNode();
                    updatedConfig.setAll((ObjectNode) originalConfig);
                    updatedConfig.put("imageWidth", puzzleResult.getImageWidth());
                    updatedConfig.put("imageHeight", puzzleResult.getImageHeight());
                    updatedConfig.put("totalPieces", puzzleResult.getTotalPieces());
                    updatedConfig.put("originalImagePath", puzzleResult.getOriginalImagePath());
                    updatedConfig.put("originalImageName", puzzleResult.getOriginalImageName());
                    updatedConfig.set("pieces", objectMapper.valueToTree(puzzleResult.getPieces()));

                    savedQuestion.setConfigurationData(updatedConfig.toString());
                    questionRepository.save(savedQuestion);

                    log.info("Successfully processed puzzle image for question {}", savedQuestion.getId());
                } else {
                    log.error("Failed to process puzzle image for question {}: {}",
                               savedQuestion.getId(), puzzleResult.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("Error processing puzzle image for question {}: {}", savedQuestion.getId(), e.getMessage());
            }
        } else if (request.getMediaFiles() != null && !request.getMediaFiles().isEmpty()) {
            try {
                List<String> mediaFilePaths = mediaService.saveMedia(request.getMediaFiles(), savedQuestion.getId(), "Questions", currentUser, null);
                savedQuestion.setMediaFilePaths(mediaFilePaths.toString());
                questionRepository.save(savedQuestion);
            } catch (Exception e) {
                log.error("Failed to save media files for question: {}", e.getMessage());
            }
        }

        return QuestionDTO.from(savedQuestion);
    }

    @Transactional(readOnly = true)
    public QuestionDTO getQuestionById(UUID id) {
        log.debug("Fetching question with ID: {}", id);

        Question question = questionRepository.findById(id)
            .orElseThrow(() -> new Validation.ResourceNotFoundException("Question", id.toString()));

        if (!question.isActive()) {
            throw new Validation.ResourceNotFoundException("Question", id.toString(), "Question with ID: " + id + " is inactive");
        }

        return QuestionDTO.from(question);
    }

    public QuestionDTO updateQuestion(UUID id, QuestionDTO request, String currentUser) {

        Question existingQuestion = questionRepository.findById(id)
            .orElseThrow(() -> new Validation.ResourceNotFoundException("Question", id.toString()));

        if (!existingQuestion.isActive()) {
            throw new Validation.ResourceNotFoundException("Question", id.toString(), "Question with ID: " + id + " is inactive");
        }

        if (request.getTitle() != null) {
            Validation.minLength(request.getTitle(), "title", 5);
            Validation.maxLength(request.getTitle(), "title", 200);
            existingQuestion.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            Validation.validateQuestionContent(request.getContent(), existingQuestion.getType());
            existingQuestion.setContent(request.getContent());
        }
        if (request.getDifficultyLevel() != null) {
            Validation.inRange(request.getDifficultyLevel(), "difficultyLevel", 1, 5);
            existingQuestion.setDifficultyLevel(request.getDifficultyLevel());
        }
        if (request.getPoints() != null) {
            Validation.positive(request.getPoints(), "points");
            existingQuestion.setPoints(request.getPoints());
        }
        if (request.getTimeLimitMinutes() != null) {
            Validation.positive(request.getTimeLimitMinutes(), "timeLimitMinutes");
            existingQuestion.setTimeLimitMinutes(request.getTimeLimitMinutes());
        }

        if (request.getConfigurationData() != null) {
            Validation.validateConfigurationData(request.getConfigurationData());

            String enrichedConfigData = enrichConfigurationWithMetadata(request.getConfigurationData(), existingQuestion.getType());

            Validation.validateQuestionConfiguration(enrichedConfigData, existingQuestion.getType());
            existingQuestion.setConfigurationData(enrichedConfigData);
        }

        if (request.getMediaFilePaths() != null) {
            Validation.validateMediaFilePaths(request.getMediaFilePaths());
            existingQuestion.setMediaFilePaths(request.getMediaFilePaths());
        }

        if (request.getAllowedMediaTypes() != null) {
            Validation.validateAllowedMediaTypes(request.getAllowedMediaTypes());
            existingQuestion.setAllowedMediaTypes(request.getAllowedMediaTypes());
        }

        if (request.isActive() != null) {
            existingQuestion.setActive(request.isActive());
        }

        existingQuestion.setUpdatedBy(currentUser);

        Question updatedQuestion = questionRepository.save(existingQuestion);

        return QuestionDTO.from(updatedQuestion);
    }

    public void deleteQuestion(UUID id, String currentUser) {

        Question question = questionRepository.findById(id)
            .orElseThrow(() -> new Validation.ResourceNotFoundException("Question", id.toString()));

        if (!question.isActive()) {
            throw new Validation.ResourceNotFoundException("Question", id.toString(), "Question with ID: " + id + " is already inactive");
        }

        question.setActive(false);
        question.setUpdatedBy(currentUser);
        questionRepository.save(question);

    }

    @Transactional(readOnly = true)
    public Page<QuestionDTO> getAllQuestions(Pageable pageable) {

        Page<Question> questions = questionRepository.findByIsActiveTrue(pageable);
        return questions.map(QuestionDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<QuestionDTO> searchQuestions(
            QuestionType type, String module, String unit, String title, Pageable pageable) {

        Page<Question> questions = questionRepository.findWithFilters(type, module, unit, title, pageable);
        return questions.map(QuestionDTO::from);
    }

    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsByType(QuestionType type) {

        List<Question> questions = questionRepository.findByTypeAndIsActiveTrue(type);
        return questions.stream().map(QuestionDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsByModule(String module) {

        List<Question> questions = questionRepository.findByModuleAndIsActiveTrue(module);
        return questions.stream().map(QuestionDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsByUnit(String unit) {

        List<Question> questions = questionRepository.findByUnitAndIsActiveTrue(unit);
        return questions.stream().map(QuestionDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QuestionDTO getRandomQuestion(QuestionType type, String module, String unit) {

        List<Question> questions = questionRepository.findRandomWithFilters(type, module, unit);

        if (questions.isEmpty()) {
            throw new Validation.ResourceNotFoundException("Question", "random", "No questions found with the specified criteria");
        }

        Question randomQuestion = questions.get(0);

        return QuestionDTO.from(randomQuestion);
    }

    // Add metadata like correct choice count to MCQ configurations
    private String enrichConfigurationWithMetadata(String configurationData, QuestionType questionType) {
        try {
            JsonNode config = objectMapper.readTree(configurationData);
            ObjectNode enrichedConfig = objectMapper.createObjectNode();
            enrichedConfig.setAll((ObjectNode) config);

            if (questionType == QuestionType.MCQ && config.has("options")) {
                JsonNode options = config.get("options");
                int correctCount = 0;

                for (JsonNode option : options) {
                    if (option.has("correct") && option.get("correct").asBoolean()) {
                        correctCount++;
                    }
                }

                enrichedConfig.put("correctChoicesCount", correctCount);
                enrichedConfig.put("isMultipleChoice", correctCount > 1);
            }

            return enrichedConfig.toString();
        } catch (Exception e) {
            log.error("Failed to enrich configuration data: {}", e.getMessage());
            return configurationData;
        }
    }

}
