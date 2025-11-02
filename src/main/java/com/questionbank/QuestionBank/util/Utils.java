package com.questionbank.QuestionBank.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

// Utility class with helper methods for strings, files, JSON, math, and text operations
public class Utils {

    private Utils() {

    }

    // String utility methods
    public static class Strings {

        private Strings() {}

        public static boolean isNullOrBlank(String value) {
            return value == null || value.isBlank();
        }

        public static String defaultIfBlank(String value, String defaultValue) {
            return isNullOrBlank(value) ? defaultValue : value;
        }
    }

    // File type detection utilities
    public static class Files {

        private static final String[] CODE_EXTENSIONS = {
            ".java", ".cpp", ".c", ".html", ".asm", ".s", ".cs", ".sh", ".py"
        };

        private Files() {}

        public static boolean isImageFile(MultipartFile file) {
            String contentType = file.getContentType();
            return contentType != null && contentType.startsWith("image/");
        }

        public static boolean isVideoFile(MultipartFile file) {
            String contentType = file.getContentType();
            return contentType != null && contentType.startsWith("video/");
        }

        public static boolean isPdfFile(MultipartFile file) {
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();
            return (contentType != null && contentType.equals("application/pdf")) ||
                   (filename != null && filename.toLowerCase().endsWith(".pdf"));
        }

        public static boolean isDocxFile(MultipartFile file) {
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();
            return (contentType != null && contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) ||
                   (filename != null && filename.toLowerCase().endsWith(".docx"));
        }

        public static boolean isCodeFile(String filename) {
            if (filename == null) {
                return false;
            }
            String lowerName = filename.toLowerCase();
            return Arrays.stream(CODE_EXTENSIONS).anyMatch(lowerName::endsWith);
        }
    }

    // JSON parsing utilities
    public static class Json {

        private Json() {}

        public static JsonNode parseConfigurationData(String configData, ObjectMapper mapper)
                throws JsonProcessingException {
            if (Strings.isNullOrBlank(configData)) {
                return mapper.createObjectNode();
            }
            return mapper.readTree(configData);
        }
    }

    // Mathematical utility methods for similarity calculations
    public static class Math {

        private Math() {}

        // Calculate cosine similarity between two vectors
        public static double cosineSimilarity(double[] vec1, double[] vec2) {
            if (vec1.length != vec2.length) {
                throw new IllegalArgumentException("Vectors must have the same dimensions");
            }

            double dotProduct = 0.0;
            double norm1 = 0.0;
            double norm2 = 0.0;

            for (int i = 0; i < vec1.length; i++) {
                dotProduct += vec1[i] * vec2[i];
                norm1 += vec1[i] * vec1[i];
                norm2 += vec2[i] * vec2[i];
            }

            if (norm1 == 0.0 || norm2 == 0.0) {
                return 0.0;
            }

            return dotProduct / (java.lang.Math.sqrt(norm1) * java.lang.Math.sqrt(norm2));
        }

        public static double dotProductSimilarity(double[] vec1, double[] vec2) {
            if (vec1.length != vec2.length) {
                throw new IllegalArgumentException("Vectors must have the same dimensions");
            }

            double similarity = 0.0;
            for (int i = 0; i < vec1.length; i++) {
                similarity += vec1[i] * vec2[i];
            }
            return similarity;
        }

        public static double characterSimilarity(String text1, String text2) {
            int lcsLength = longestCommonSubsequence(text1, text2);
            int maxLength = java.lang.Math.max(text1.length(), text2.length());
            if (maxLength == 0) return 0.0;
            return (double) lcsLength / maxLength;
        }

        // Dynamic programming approach with space optimization
        public static int longestCommonSubsequence(String text1, String text2) {
            int m = text1.length();
            int n = text2.length();
            int[] prev = new int[n + 1];
            int[] curr = new int[n + 1];

            for (int i = 1; i <= m; i++) {
                for (int j = 1; j <= n; j++) {
                    if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                        curr[j] = prev[j - 1] + 1;
                    } else {
                        curr[j] = java.lang.Math.max(curr[j - 1], prev[j]);
                    }
                }
                // Swap arrays for space efficiency
                int[] temp = prev;
                prev = curr;
                curr = temp;
            }
            return prev[n];
        }
    }

    // Text normalization utilities
    public static class Text {

        private Text() {}

        // Normalize text for comparison by converting to lowercase and removing special characters
        public static String normalize(String text) {
            if (text == null) return "";
            text = text.toLowerCase();
            text = text.replaceAll("\\s+", " ").trim();
            text = text.replaceAll("[\\[\\]{}()\"]", "");
            return text;
        }
    }

    // Application-wide constants
    public static class Constants {

        private Constants() {}

        public static final String EMPTY_JSON_ARRAY = "[]";
        public static final String EMPTY_JSON_OBJECT = "{}";

        public static final String SYSTEM_USER = "system";
    }
}
