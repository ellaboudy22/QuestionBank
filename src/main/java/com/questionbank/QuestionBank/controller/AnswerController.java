package com.questionbank.QuestionBank.controller;

import com.questionbank.QuestionBank.dto.AnswerDTO;
import com.questionbank.QuestionBank.service.AnswerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// REST controller for answer submissions and retrieval
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final AnswerService answerService;

    @Autowired
    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping("/submit-answer")
    public ResponseEntity<AnswerDTO> submitAnswer(
            @RequestPart("request") @Valid AnswerDTO request,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles) {

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            request.setMediaFiles(mediaFiles);
        }

        AnswerDTO response = answerService.createAnswer(request, mediaFiles);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnswerDTO> getAnswerById(@PathVariable UUID id) {
        AnswerDTO response = answerService.getAnswerById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<AnswerDTO>> getAllAnswers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AnswerDTO> responses = answerService.getAllAnswers(pageable, filter);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<Page<AnswerDTO>> getAnswersByQuestionId(
            @PathVariable UUID questionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AnswerDTO> responses = answerService.getAnswersByQuestionId(questionId, pageable, filter);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnswerDTO> updateAnswer(
            @PathVariable UUID id,
            @Valid @RequestBody AnswerDTO request) {
        AnswerDTO response = answerService.updateAnswer(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnswer(@PathVariable UUID id) {
        answerService.deleteAnswer(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDeleteAnswer(@PathVariable UUID id) {
        answerService.hardDeleteAnswer(id);
        return ResponseEntity.noContent().build();
    }

}
