package com.questionbank.QuestionBank.controller;

import com.questionbank.QuestionBank.dto.AiCorrectionDTO;
import com.questionbank.QuestionBank.service.AiCorrectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// REST controller for AI-based answer correction
@RestController
@RequestMapping("/api/v1/ai-correction")
public class AiCorrectionController {

    private static final Logger log = LoggerFactory.getLogger(AiCorrectionController.class);

    private final AiCorrectionService aiCorrectionService;

    @Autowired
    public AiCorrectionController(AiCorrectionService aiCorrectionService) {
        this.aiCorrectionService = aiCorrectionService;
    }

    @PostMapping("/correct/{answerId}")
    public CompletableFuture<ResponseEntity<AiCorrectionDTO>> correctAnswerById(@PathVariable UUID answerId) {

        return aiCorrectionService.correctAnswerById(answerId)
            .thenApply(result -> ResponseEntity.ok(result))
            .exceptionally(throwable -> {
                log.error("Error during AI correction for answer ID {}: {}",
                           answerId, throwable.getMessage(), throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AiCorrectionDTO(answerId, "AI correction failed: " + throwable.getMessage()));
            });
    }

    @PostMapping("/correct/batch")
    public CompletableFuture<ResponseEntity<AiCorrectionDTO[]>> batchCorrectAnswers(@RequestBody UUID[] answerIds) {

        return aiCorrectionService.batchCorrectAnswers(answerIds)
            .thenApply(result -> ResponseEntity.ok(result))
            .exceptionally(throwable -> {
                log.error("Error during batch AI correction: {}", throwable.getMessage(), throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new AiCorrectionDTO[0]);
            });
    }
}
