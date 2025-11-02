package com.questionbank.QuestionBank.controller;

import com.questionbank.QuestionBank.dto.QuestionDTO;
import com.questionbank.QuestionBank.entity.QuestionType;
import com.questionbank.QuestionBank.service.QuestionService;
import com.questionbank.QuestionBank.util.Utils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// REST controller for question CRUD operations
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    @Autowired
    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<QuestionDTO> createQuestion(
            @RequestPart("request") @Valid QuestionDTO request,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            Authentication authentication) {

        if (mediaFiles != null && !mediaFiles.isEmpty()) {
            request.setMediaFiles(mediaFiles);
        }

        QuestionDTO response = questionService.createQuestion(request, getCurrentUser(authentication));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionDTO> getQuestionById(@PathVariable UUID id) {

        QuestionDTO response = questionService.getQuestionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/random")
    public ResponseEntity<QuestionDTO> getRandomQuestion(
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String unit) {

        QuestionDTO response = questionService.getRandomQuestion(type, module, unit);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionDTO> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionDTO request,
            Authentication authentication) {

        QuestionDTO response = questionService.updateQuestion(id, request, getCurrentUser(authentication));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID id,
            Authentication authentication) {

        questionService.deleteQuestion(id, getCurrentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<QuestionDTO>> getAllQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ?
                           Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<QuestionDTO> questions = questionService.getAllQuestions(pageable);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<QuestionDTO>> searchQuestions(
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = Sort.by(sortDir.equalsIgnoreCase("desc") ?
                           Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<QuestionDTO> questions = questionService.searchQuestions(type, module, unit, title, pageable);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<QuestionDTO>> getQuestionsByType(@PathVariable QuestionType type) {

        List<QuestionDTO> questions = questionService.getQuestionsByType(type);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/module/{module}")
    public ResponseEntity<List<QuestionDTO>> getQuestionsByModule(@PathVariable String module) {

        List<QuestionDTO> questions = questionService.getQuestionsByModule(module);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/unit/{unit}")
    public ResponseEntity<List<QuestionDTO>> getQuestionsByUnit(@PathVariable String unit) {

        List<QuestionDTO> questions = questionService.getQuestionsByUnit(unit);
        return ResponseEntity.ok(questions);
    }

    private String getCurrentUser(Authentication authentication) {
        return authentication != null ? authentication.getName() : Utils.Constants.SYSTEM_USER;
    }
}
