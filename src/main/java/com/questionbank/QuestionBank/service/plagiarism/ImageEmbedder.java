package com.questionbank.QuestionBank.service.plagiarism;

import ai.onnxruntime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// Service for generating image embeddings using ONNX ResNet50 model for plagiarism detection
@Service
public class ImageEmbedder {

    private static final Logger log = LoggerFactory.getLogger(ImageEmbedder.class);
    private final ObjectMapper objectMapper;

    @Value("${plagiarism.image.onnx.enabled:true}")
    private boolean onnxEnabled;

    @Value("${plagiarism.image.onnx.model.path:models/image-embedding-resnet50.onnx}")
    private String modelPath;

    @Value("${plagiarism.image.embedding.dimensions:2048}")
    private int embeddingDimensions;

    @Value("${plagiarism.image.input.width:224}")
    private int inputWidth;

    @Value("${plagiarism.image.input.height:224}")
    private int inputHeight;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;

    public ImageEmbedder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!onnxEnabled) {
            log.warn("ONNX is disabled. Image plagiarism detection will not work.");
            return;
        }

        try {
            env = OrtEnvironment.getEnvironment();
            loadModel();
        } catch (Exception e) {
            log.error("Failed to initialize ONNX: {}", e.getMessage());
            throw new RuntimeException("ONNX initialization required for image plagiarism detection", e);
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
            log.info("ONNX image model loaded successfully: {}D embeddings", embeddingDimensions);
        } catch (Exception e) {
            log.error("Failed to load ONNX model: {}", e.getMessage());
        }
    }

    public double[] extractImageFeatures(String imagePath) throws IOException {
        byte[] imageData = Files.readAllBytes(Paths.get(imagePath));
        return extractImageFeatures(imageData);
    }

    @Cacheable(value = "imageFeatures", key = "#root.methodName + '_' + T(java.util.Arrays).hashCode(#imageData)")
    public double[] extractImageFeatures(byte[] imageData) throws IOException {
        if (imageData == null || imageData.length == 0) {
            throw new IOException("Image data is null or empty");
        }

        if (!modelLoaded) {
            throw new IOException("ONNX model not loaded. Cannot extract image features.");
        }

        try {
            Mat imageBytes = new Mat(imageData.length, 1, CV_8UC1);
            imageBytes.data().put(imageData);
            Mat image = imdecode(imageBytes, IMREAD_COLOR);

            if (image.empty()) {
                image = imdecode(imageBytes, IMREAD_GRAYSCALE);
                if (image.empty()) {
                    throw new IOException("Failed to decode image");
                }
            }

            return extractOnnxFeatures(image);
        } catch (Exception e) {
            log.error("Error extracting features: {}", e.getMessage());
            throw new IOException("Failed to extract features: " + e.getMessage(), e);
        }
    }

    private double[] extractOnnxFeatures(Mat image) throws Exception {
        Mat preprocessed = preprocessForOnnx(image);
        float[] inputArray = matToFloatArray(preprocessed);
        float[] embedding = runOnnxInference(inputArray);

        double[] result = new double[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            result[i] = embedding[i];
        }

        preprocessed.release();
        return result;
    }

    // Preprocess image: resize, convert to RGB, normalize with ImageNet stats
    private Mat preprocessForOnnx(Mat image) {
        Mat resized = new Mat();
        resize(image, resized, new Size(inputWidth, inputHeight));

        Mat rgb = new Mat();
        cvtColor(resized, rgb, COLOR_BGR2RGB);

        Mat floatImage = new Mat();
        rgb.convertTo(floatImage, CV_32F, 1.0 / 255.0, 0.0);

        Mat mean = new Mat(inputHeight, inputWidth, CV_32FC3, new Scalar(0.485, 0.456, 0.406, 0.0));
        Mat std = new Mat(inputHeight, inputWidth, CV_32FC3, new Scalar(0.229, 0.224, 0.225, 0.0));
        subtract(floatImage, mean, floatImage);
        divide(floatImage, std, floatImage);

        return floatImage;
    }

    // Convert OpenCV Mat to NCHW format (channels, height, width) for ONNX
    private float[] matToFloatArray(Mat image) {
        int height = image.rows();
        int width = image.cols();
        int channels = image.channels();
        float[] result = new float[channels * height * width];

        FloatBuffer buffer = image.createBuffer();
        for (int c = 0; c < channels; c++) {
            for (int h = 0; h < height; h++) {
                for (int w = 0; w < width; w++) {
                    int srcIdx = (h * width + w) * channels + c;
                    int dstIdx = c * height * width + h * width + w;
                    result[dstIdx] = buffer.get(srcIdx);
                }
            }
        }
        return result;
    }

    private float[] runOnnxInference(float[] inputArray) throws OrtException {
        long[] shape = {1, 3, inputHeight, inputWidth};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArray), shape);

        try {
            String inputName = session.getInputNames().iterator().next();
            Map<String, OnnxTensor> inputs = Map.of(inputName, inputTensor);
            OrtSession.Result result = session.run(inputs);

            Object output = result.get(0).getValue();
            float[] embedding;
            if (output instanceof float[][]) {
                embedding = ((float[][]) output)[0];
            } else {
                embedding = (float[]) output;
            }

            result.close();
            return embedding;
        } finally {
            inputTensor.close();
        }
    }

    public String serializeEmbeddings(double[] embeddings) {
        try {
            Map<String, Object> embeddingData = new HashMap<>();
            embeddingData.put("features", embeddings);
            embeddingData.put("dimensions", embeddings.length);
            embeddingData.put("extractedAt", System.currentTimeMillis());

            return objectMapper.writeValueAsString(embeddingData);
        } catch (Exception e) {
            log.error("Error serializing embeddings: {}", e.getMessage());
            return "{}";
        }
    }

    // Deserialize embeddings from JSON with fallback handling for different formats
    @SuppressWarnings("unchecked")
    public double[] deserializeEmbeddings(String embeddingJson) {
        try {
            if (embeddingJson == null || embeddingJson.trim().isEmpty() || embeddingJson.equals("{}")) {
                return new double[0];
            }

            Map<String, Object> embeddingData = objectMapper.readValue(embeddingJson, Map.class);
            Object featuresObj = embeddingData.get("features");

            if (featuresObj == null) {
                return new double[0];
            }

            if (featuresObj instanceof List) {
                List<?> featuresList = (List<?>) featuresObj;
                double[] result = new double[featuresList.size()];
                for (int i = 0; i < featuresList.size(); i++) {
                    Object item = featuresList.get(i);
                    if (item instanceof Number) {
                        result[i] = ((Number) item).doubleValue();
                    } else if (item instanceof String) {
                        try {
                            result[i] = Double.parseDouble((String) item);
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse number from string at index {}: {}", i, item);
                            result[i] = 0.0;
                        }
                    } else {
                        log.warn("Unknown item type at index {}: {}", i, item != null ? item.getClass() : "null");
                        result[i] = 0.0;
                    }
                }
                return result;
            } else if (featuresObj instanceof double[]) {
                return (double[]) featuresObj;
            } else if (featuresObj instanceof String) {

                try {
                    String str = (String) featuresObj;
                    if (str.startsWith("[")) {

                        List<?> parsed = objectMapper.readValue(str, List.class);
                        double[] result = new double[parsed.size()];
                        for (int i = 0; i < parsed.size(); i++) {
                            Object item = parsed.get(i);
                            if (item instanceof Number) {
                                result[i] = ((Number) item).doubleValue();
                            } else if (item instanceof String) {
                                result[i] = Double.parseDouble((String) item);
                            }
                        }
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse features from string: {}", e.getMessage());
                }
                return new double[0];
            } else {
                log.warn("Unknown features type: {}", featuresObj.getClass());
                return new double[0];
            }
        } catch (Exception e) {
            log.error("Error deserializing embeddings: {}", e.getMessage());
            return new double[0];
        }
    }

    public double calculateCosineSimilarity(double[] embedding1, double[] embedding2) {
        return com.questionbank.QuestionBank.util.Utils.Math.cosineSimilarity(embedding1, embedding2);
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) {
                session.close();
                log.info("ONNX session closed");
            }
            if (env != null) {
                env.close();
                log.info("ONNX environment closed");
            }
        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage());
        }
    }
}
