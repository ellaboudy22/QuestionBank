package com.questionbank.QuestionBank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.questionbank.QuestionBank.entity.Answer;
import com.questionbank.QuestionBank.entity.Question;
import com.questionbank.QuestionBank.exception.Validation;
import com.questionbank.QuestionBank.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Service for compiling and testing code submissions using Judge0 API
@Service
public class CodeCompilationService {

    private static final Logger log = LoggerFactory.getLogger(CodeCompilationService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MediaService mediaService;

    @Value("${judge0.api.key}")
    private String judge0ApiKey;

    @Value("${judge0.api.url:https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true}")
    private String judge0ApiUrl;

    @Value("${judge0.api.host:judge0-ce.p.rapidapi.com}")
    private String judge0ApiHost;

    private static final Map<String, Integer> LANGUAGE_IDS = new HashMap<>();

    static {
        LANGUAGE_IDS.put("java", 62);
        LANGUAGE_IDS.put("cpp", 54);
        LANGUAGE_IDS.put("python", 71);
        LANGUAGE_IDS.put("c", 50);
        LANGUAGE_IDS.put("csharp", 51);
        LANGUAGE_IDS.put("assembly", 45);
    }

    @Autowired
    public CodeCompilationService(ObjectMapper objectMapper, MediaService mediaService) {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.mediaService = mediaService;
    }

    public CompletableFuture<CodeCompilationResult> compileCode(String sourceCode, String language, String testInput, String expectedOutput) {
        return compileCode(sourceCode, language, testInput, expectedOutput, false);
    }

    public CompletableFuture<CodeCompilationResult> compileCode(String sourceCode, String language, String testInput, String expectedOutput, boolean pseudoCompile) {
        Validation.notNullOrEmpty(sourceCode, "sourceCode");
        Validation.minLength(sourceCode, "sourceCode", 1);
        Validation.maxLength(sourceCode, "sourceCode", 50000);

        Validation.notNullOrEmpty(language, "language");

        if (testInput != null) {
            Validation.maxLength(testInput, "testInput", 10000);
        }

        if (expectedOutput != null) {
            Validation.maxLength(expectedOutput, "expectedOutput", 10000);
        }

        if (!isLanguageSupported(language)) {
            log.warn("Language {} not supported for compilation", language);
            return CompletableFuture.completedFuture(createUnsupportedLanguageResult(language));
        }

        try {
            if (pseudoCompile) {
                return performPseudoCompilation(sourceCode, language);
            } else {
                return performCompilation(sourceCode, language, testInput, expectedOutput);
            }
        } catch (Exception e) {
            log.error("Error during code compilation: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(createErrorResult(e.getMessage()));
        }
    }

    public boolean isLanguageSupported(String language) {
        return language != null && LANGUAGE_IDS.containsKey(language.toLowerCase());
    }

    // Execute code compilation and testing via Judge0 API
    private CompletableFuture<CodeCompilationResult> performCompilation(String sourceCode, String language, String testInput, String expectedOutput) {
        String requestBody = "";
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("language_id", LANGUAGE_IDS.get(language.toLowerCase()));
            request.put("source_code", sourceCode);
            request.put("stdin", testInput != null ? testInput : "");
            request.put("expected_output", expectedOutput != null ? expectedOutput : "");

            requestBody = objectMapper.writeValueAsString(request);

        } catch (Exception e) {
            log.error("Error building compilation request: {}", e.getMessage());
            throw new RuntimeException("Failed to build compilation request", e);
        }

        return webClient.post()
                .uri(judge0ApiUrl)
                .header("X-RapidAPI-Key", judge0ApiKey)
                .header("X-RapidAPI-Host", judge0ApiHost)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parseCompilationResponse(response, language, expectedOutput))
                .doOnError(error -> log.error("Judge0 API error: {}", error.getMessage()))
                .toFuture();
    }

    private CompletableFuture<CodeCompilationResult> performPseudoCompilation(String sourceCode, String language) {
        String requestBody = "";
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("language_id", LANGUAGE_IDS.get(language.toLowerCase()));
            request.put("source_code", sourceCode);
            request.put("stdin", "");
            request.put("cpu_time_limit", "1");
            request.put("enable_network", false);

            requestBody = objectMapper.writeValueAsString(request);

        } catch (Exception e) {
            log.error("Error building pseudo-compilation request: {}", e.getMessage());
            throw new RuntimeException("Failed to build pseudo-compilation request", e);
        }

        return webClient.post()
                .uri(judge0ApiUrl)
                .header("X-RapidAPI-Key", judge0ApiKey)
                .header("X-RapidAPI-Host", judge0ApiHost)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parsePseudoCompilationResponse(response, language))
                .doOnError(error -> log.error("Judge0 API error during pseudo-compilation: {}", error.getMessage()))
                .toFuture();
    }

    // Parse Judge0 response and determine compilation success
    private CodeCompilationResult parseCompilationResponse(String response, String language, String expectedOutput) {

        try {
            JsonNode responseNode = objectMapper.readTree(response);

            CodeCompilationResult result = new CodeCompilationResult();
            result.setLanguage(language);
            result.setCompilationMethod("Compiler");

            if (responseNode.has("status")) {
                JsonNode status = responseNode.get("status");
                if (status.has("id")) {
                    int statusId = status.get("id").asInt();
                    result.setCompilationSuccessful(statusId == 3);
                    result.setStatusId(statusId);
                    result.setStatusDescription(status.has("description") ? status.get("description").asText() : "");
                }
            }

            if (responseNode.has("stdout")) {
                result.setOutput(responseNode.get("stdout").asText(""));
            }

            if (responseNode.has("stderr")) {
                result.setErrorOutput(responseNode.get("stderr").asText(""));
            }

            if (responseNode.has("compile_output")) {
                result.setCompileOutput(responseNode.get("compile_output").asText(""));
            }

            if (responseNode.has("time")) {
                result.setExecutionTime(responseNode.get("time").asText(""));
            }

            if (responseNode.has("memory")) {
                result.setMemoryUsage(responseNode.get("memory").asText(""));
            }

            // Status 3 = Accepted
            if (result.getStatusId() == 3) {
                result.setCompilationSuccessful(true);
                result.setFeedback("Code compiled and executed successfully! Output: " + result.getOutput());
            } else if (result.getStatusId() == 4) {
                // Status 4 = Wrong Answer
                result.setCompilationSuccessful(true);
                result.setFeedback("Code executed successfully but output doesn't match expected. Got: '" + result.getOutput().trim() + "', Expected: '" + expectedOutput + "'");
            } else if (result.getStatusId() == 5) {
                result.setCompilationSuccessful(false);
                result.setFeedback("Runtime error: " + result.getErrorOutput());
            } else if (result.getStatusId() == 6) {
                result.setCompilationSuccessful(false);
                result.setFeedback("Compilation error: " + result.getCompileOutput());
            } else {
                result.setCompilationSuccessful(false);
                result.setFeedback("Execution failed with status: " + result.getStatusDescription());
            }

            return result;

        } catch (Exception e) {
            log.error("Error parsing compilation response: {}", e.getMessage());
            return createErrorResult("Failed to parse compilation response: " + e.getMessage());
        }
    }

    private CodeCompilationResult parsePseudoCompilationResponse(String response, String language) {

        try {
            JsonNode responseNode = objectMapper.readTree(response);

            CodeCompilationResult result = new CodeCompilationResult();
            result.setLanguage(language);
            result.setCompilationMethod("Pseudo-Compiler (Syntax Check)");

            if (responseNode.has("status")) {
                JsonNode status = responseNode.get("status");
                if (status.has("id")) {
                    int statusId = status.get("id").asInt();
                    result.setStatusId(statusId);
                    result.setStatusDescription(status.has("description") ? status.get("description").asText() : "");
                }
            }

            if (responseNode.has("compile_output")) {
                result.setCompileOutput(responseNode.get("compile_output").asText(""));
            }

            if (responseNode.has("stderr")) {
                result.setErrorOutput(responseNode.get("stderr").asText(""));
            }

            if (result.getStatusId() == 3 || result.getStatusId() == 4) {
                result.setCompilationSuccessful(true);
                result.setFeedback("Syntax check passed! Code is syntactically valid.");
            } else if (result.getStatusId() == 6) {
                result.setCompilationSuccessful(false);
                result.setFeedback("Syntax error detected: " + result.getCompileOutput());
            } else if (result.getStatusId() == 5) {
                result.setCompilationSuccessful(true);
                result.setFeedback("Syntax check passed, but runtime issues may exist (not executed).");
            } else {
                result.setCompilationSuccessful(false);
                result.setFeedback("Syntax validation failed with status: " + result.getStatusDescription());
            }

            return result;

        } catch (Exception e) {
            log.error("Error parsing pseudo-compilation response: {}", e.getMessage());
            return createErrorResult("Failed to parse pseudo-compilation response: " + e.getMessage());
        }
    }

    private CodeCompilationResult createUnsupportedLanguageResult(String language) {
        CodeCompilationResult result = new CodeCompilationResult();
        result.setLanguage(language);
        result.setCompilationSuccessful(false);
        result.setFeedback("Language '" + language + "' is not supported for compilation");
        result.setCompilationMethod("Unsupported");
        return result;
    }

    private CodeCompilationResult createErrorResult(String errorMessage) {
        CodeCompilationResult result = new CodeCompilationResult();
        result.setCompilationSuccessful(false);
        result.setFeedback("Compilation error: " + errorMessage);
        result.setCompilationMethod("Error");
        return result;
    }

    // Extract code from uploaded file or answer content
    public String extractCodeContent(Answer answer) {

        // First try to find code file in media files
        try {
            List<Resource> mediaFiles = mediaService.getMediaFiles(answer.getId(), "Answers");
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                for (Resource resource : mediaFiles) {
                    String filename = resource.getFilename();
                    if (filename != null && Utils.Files.isCodeFile(filename)) {
                        try (java.io.InputStream inputStream = resource.getInputStream()) {
                            String codeContent = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            return codeContent;
                        } catch (Exception e) {
                            log.warn("Failed to read code file '{}': {}", filename, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No media files found for answer {}: {}", answer.getId(), e.getMessage());
        }

        String content = answer.getContent();
        if (content != null && !content.trim().isEmpty()) {
            return content;
        }

        return null;
    }

    public String extractLanguageFromQuestion(Question question) {

        try {
            JsonNode configNode = Utils.Json.parseConfigurationData(question.getConfigurationData(), objectMapper);

            JsonNode fieldNode = configNode.get("language");
            if (fieldNode != null && fieldNode.isTextual()) {
                String language = fieldNode.asText().toLowerCase();
                if (isLanguageSupported(language)) {
                    return language;
                }
            }

            return "python";

        } catch (Exception e) {
            log.warn("Error parsing question configuration for language: {}", e.getMessage());
            return "python";
        }
    }

    // Extract test case input/output from question configuration
    public Map<String, String> extractTestCasesFromQuestion(Question question) {

        try {
            JsonNode configNode = Utils.Json.parseConfigurationData(question.getConfigurationData(), objectMapper);

            JsonNode testCasesNode = configNode.get("testCases");
            if (testCasesNode != null) {
                JsonNode testCasesArray = null;

                if (testCasesNode.isTextual()) {
                    String testCasesJsonString = testCasesNode.asText();
                    testCasesArray = objectMapper.readTree(testCasesJsonString);
                } else if (testCasesNode.isArray()) {
                    testCasesArray = testCasesNode;
                }

                if (testCasesArray != null && testCasesArray.isArray() && testCasesArray.size() > 0) {
                    JsonNode firstTestCase = testCasesArray.get(0);
                    Map<String, String> testCase = new HashMap<>();

                    if (firstTestCase.has("input")) {
                        testCase.put("input", firstTestCase.get("input").asText(""));
                    }
                    if (firstTestCase.has("expectedOutput")) {
                        testCase.put("output", firstTestCase.get("expectedOutput").asText(""));
                    } else if (firstTestCase.has("output")) {
                        testCase.put("output", firstTestCase.get("output").asText(""));
                    }

                    if (!testCase.isEmpty()) {
                        return testCase;
                    }
                }
            }

            Map<String, String> testCase = new HashMap<>();
            if (configNode.has("input")) {
                testCase.put("input", configNode.get("input").asText(""));
            }
            if (configNode.has("output")) {
                testCase.put("output", configNode.get("output").asText(""));
            }

            return testCase.isEmpty() ? null : testCase;

        } catch (Exception e) {
            log.warn("Error parsing question configuration for test cases: {}", e.getMessage());
            return null;
        }
    }
    public static class CodeCompilationResult {
        private String language;
        private boolean compilationSuccessful;
        private String feedback;
        private String compilationMethod;
        private int statusId;
        private String statusDescription;
        private String output;
        private String errorOutput;
        private String compileOutput;
        private String executionTime;
        private String memoryUsage;

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public boolean isCompilationSuccessful() { return compilationSuccessful; }
        public void setCompilationSuccessful(boolean compilationSuccessful) { this.compilationSuccessful = compilationSuccessful; }

        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }

        public String getCompilationMethod() { return compilationMethod; }
        public void setCompilationMethod(String compilationMethod) { this.compilationMethod = compilationMethod; }

        public int getStatusId() { return statusId; }
        public void setStatusId(int statusId) { this.statusId = statusId; }

        public String getStatusDescription() { return statusDescription; }
        public void setStatusDescription(String statusDescription) { this.statusDescription = statusDescription; }

        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }

        public String getErrorOutput() { return errorOutput; }
        public void setErrorOutput(String errorOutput) { this.errorOutput = errorOutput; }

        public String getCompileOutput() { return compileOutput; }
        public void setCompileOutput(String compileOutput) { this.compileOutput = compileOutput; }

        public String getExecutionTime() { return executionTime; }
        public void setExecutionTime(String executionTime) { this.executionTime = executionTime; }

        public String getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(String memoryUsage) { this.memoryUsage = memoryUsage; }

    }
}
