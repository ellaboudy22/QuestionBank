package com.questionbank.QuestionBank.service.plagiarism;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Service for generating text embeddings using ONNX model for plagiarism detection
@Service
public class TextEmbedder {

    private static final Logger log = LoggerFactory.getLogger(TextEmbedder.class);

    @Value("${plagiarism.text.onnx.enabled:true}")
    private boolean onnxEnabled;

    @Value("${plagiarism.text.onnx.model.path:models/text-embedding-all-MiniLM-L6-v2.onnx}")
    private String modelPath;

    @Value("${plagiarism.text.embedding.dimensions:384}")
    private int embeddingDimensions;

    @Value("${plagiarism.text.max.length:512}")
    private int maxLength;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;

    @PostConstruct
    public void init() {
        if (!onnxEnabled) {
            log.warn("ONNX is disabled. Text plagiarism detection will not work.");
            return;
        }

        try {
            env = OrtEnvironment.getEnvironment();
            loadModel();
        } catch (Exception e) {
            log.error("Failed to initialize ONNX: {}", e.getMessage());
            throw new RuntimeException("ONNX initialization required for text plagiarism detection", e);
        }
    }

    private void loadModel() {
        try {
            Path modelFile;
            try {
                modelFile = Paths.get(getClass().getClassLoader().getResource(modelPath).toURI());
            } catch (Exception e) {
                modelFile = Paths.get(modelPath);
            }

            if (!Files.exists(modelFile)) {
                throw new RuntimeException("ONNX model file not found: " + modelPath);
            }

            session = env.createSession(modelFile.toString(), new OrtSession.SessionOptions());
            modelLoaded = true;
            log.info("ONNX text model loaded successfully: {}D embeddings", embeddingDimensions);
        } catch (Exception e) {
            log.error("Failed to load ONNX text model: {}", e.getMessage());
            throw new RuntimeException("Failed to load ONNX model", e);
        }
    }

    @Cacheable(value = "textEmbeddings", key = "#text.hashCode()")
    public double[] extractTextEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new double[embeddingDimensions];
        }

        if (!modelLoaded) {
            throw new RuntimeException("ONNX model not loaded. Cannot extract text embeddings.");
        }

        try {
            return extractOnnxEmbedding(text);
        } catch (Exception e) {
            log.error("ONNX extraction failed: {}", e.getMessage());
            throw new RuntimeException("Failed to extract text embedding", e);
        }
    }

    // Generate embedding using ONNX model with tokenization and pooling
    private double[] extractOnnxEmbedding(String text) throws OrtException {
        long[] inputIds = tokenize(text);
        long[] attentionMask = createAttentionMask(inputIds);

        long[] shape = {1, maxLength};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
        OnnxTensor maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);

        try {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputTensor);
            inputs.put("attention_mask", maskTensor);

            OrtSession.Result result = session.run(inputs);
            Object output = result.get(0).getValue();

            float[] embedding;
            if (output instanceof float[][][]) {
                embedding = meanPooling(((float[][][]) output)[0], attentionMask);
            } else {
                embedding = ((float[][]) output)[0];
            }

            embedding = normalize(embedding);

            double[] doubleEmbedding = new double[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                doubleEmbedding[i] = embedding[i];
            }

            result.close();
            return doubleEmbedding;
        } finally {
            inputTensor.close();
            maskTensor.close();
        }
    }

    // Simple tokenization using word hashing (not production-grade)
    private long[] tokenize(String text) {
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim().split("\\s+");
        long[] tokens = new long[maxLength];

        tokens[0] = 101;
        int idx = 1;

        for (String word : words) {
            if (idx >= maxLength - 1) break;
            tokens[idx++] = Math.abs(word.hashCode()) % 30000 + 1000;
        }

        if (idx < maxLength) {
            tokens[idx] = 102;
        }

        return tokens;
    }

    private long[] createAttentionMask(long[] inputIds) {
        long[] mask = new long[maxLength];
        for (int i = 0; i < maxLength; i++) {
            mask[i] = (inputIds[i] != 0) ? 1 : 0;
        }
        return mask;
    }

    // Average token embeddings to get sentence embedding
    private float[] meanPooling(float[][] tokenEmbeddings, long[] attentionMask) {
        int hiddenSize = tokenEmbeddings[0].length;
        float[] pooled = new float[hiddenSize];
        int validTokens = 0;

        for (int i = 0; i < tokenEmbeddings.length && i < maxLength; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < hiddenSize; j++) {
                    pooled[j] += tokenEmbeddings[i][j];
                }
                validTokens++;
            }
        }

        if (validTokens > 0) {
            for (int j = 0; j < hiddenSize; j++) {
                pooled[j] /= validTokens;
            }
        }

        return pooled;
    }

    private float[] normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    public double calculateSimilarity(double[] emb1, double[] emb2) {
        return com.questionbank.QuestionBank.util.Utils.Math.dotProductSimilarity(emb1, emb2);
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) {
                session.close();
                log.info("ONNX text session closed");
            }
            if (env != null) {
                env.close();
            }
        } catch (Exception e) {
            log.error("Cleanup error: {}", e.getMessage());
        }
    }
}
