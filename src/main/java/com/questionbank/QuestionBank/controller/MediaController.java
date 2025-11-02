package com.questionbank.QuestionBank.controller;

import com.questionbank.QuestionBank.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// REST controller for media file downloads and deletion
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath) {

        Resource resource = mediaService.getFileAsResource(filePath);

        String filename = java.nio.file.Paths.get(filePath).getFileName().toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteFile(@RequestParam String filePath) {

        mediaService.deleteFile(filePath);
        return ResponseEntity.noContent().build();
    }
}
