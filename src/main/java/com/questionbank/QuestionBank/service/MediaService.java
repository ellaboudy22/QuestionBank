package com.questionbank.QuestionBank.service;

import com.questionbank.QuestionBank.exception.Validation;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Service for handling media file uploads, downloads, and processing
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    @Value("${media.upload.max-file-size:52428800}")
    private long maxFileSize;

    @Value("${media.upload.allowed-mime-types:image/*,video/*,audio/*,application/pdf,text/*,application/zip,application/x-rar-compressed}")
    private String allowedMimeTypes;

    @Autowired
    private PlagiarismService plagiarismService;

    private final Path dataPath;

    public MediaService(@Value("${media.upload.path:Data}") String dataPath) {
        this.dataPath = Paths.get(dataPath).toAbsolutePath().normalize();
        try {
            createDataDirectory();
        } catch (Exception e) {
            log.warn("Could not create data directory: {}", e.getMessage());
        }
    }

    private void createDataDirectory() {
        try {
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
            }
        } catch (IOException e) {
            throw new Validation.ValidationException("Could not create data directory: " + e.getMessage());
        }
    }

    public List<String> saveMedia(List<MultipartFile> files, UUID id, String type, String user, UUID answerId) {
        Validation.notNullOrEmptyMedia(files, "files");
        Validation.notNullOrEmpty(id.toString(), "id");
        Validation.notNullOrEmpty(type, "type");
        Validation.notNullOrEmpty(user, "user");

        List<String> filePaths = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                validateFile(file);

                try {
                    Path mediaPath = dataPath.resolve(type).resolve(id.toString());
                    if (!Files.exists(mediaPath)) {
                        Files.createDirectories(mediaPath);
                    }

                    String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
                    String fileExtension = getFileExtension(originalFilename);
                    String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

                    Path targetPath = mediaPath.resolve(uniqueFilename);
                    Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                    String relativePath = "Data/" + type + "/" + id.toString() + "/" + uniqueFilename;
                    filePaths.add(relativePath);

                    if (answerId != null) {
                        try {
                            plagiarismService.save(relativePath, file.getContentType(), user, id, answerId);
                        } catch (Exception e) {
                            log.warn("Failed to save to plagiarism system: {}", e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    throw new Validation.ValidationException("Failed to save media file: " + e.getMessage());
                }
            }
        }

        return filePaths;
    }

    public void deleteFile(String filePath) {
        try {
            String normalizedPath = filePath.trim().replaceAll("^/+", "").replaceAll("/+$", "");

            Path completePath = dataPath.resolve(normalizedPath).normalize();

            if (!completePath.startsWith(dataPath)) {
                throw new RuntimeException("Access denied: path is outside data directory: " + filePath);
            }

            Files.deleteIfExists(completePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + filePath, e);
        }
    }

    public List<Resource> getMediaFiles(UUID id, String type) {
        List<Resource> resources = new ArrayList<>();
        Path mediaPath = dataPath.resolve(type).resolve(id.toString());

        if (Files.exists(mediaPath)) {
            try {
                Files.list(mediaPath)
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            Resource resource = new UrlResource(filePath.toUri());
                            if (resource.exists() && resource.isReadable()) {
                                resources.add(resource);
                            }
                        } catch (MalformedURLException e) {
                            log.error("Failed to read media file: {} - {}", filePath, e.getMessage());
                        }
                    });
            } catch (IOException e) {
                throw new RuntimeException("Failed to read media files", e);
            }
        }

        return resources;
    }

    public List<String> getMediaFilePaths(UUID id, String type) {
        List<String> filePaths = new ArrayList<>();
        Path mediaPath = dataPath.resolve(type).resolve(id.toString());

        if (Files.exists(mediaPath)) {
            try {
                Files.list(mediaPath)
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        String relativePath = "Data/" + type + "/" + id.toString() + "/" + filePath.getFileName().toString();
                        filePaths.add(relativePath);
                    });
            } catch (IOException e) {
                throw new RuntimeException("Failed to read media file paths", e);
            }
        }

        return filePaths;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File cannot be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("File size exceeds maximum allowed size of " +
                                        (maxFileSize / (1024 * 1024)) + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isAllowedMimeType(contentType)) {
            throw new RuntimeException("File type not allowed: " + contentType);
        }
    }

    private boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        String[] allowedTypes = allowedMimeTypes.split(",");
        for (String allowedType : allowedTypes) {
            allowedType = allowedType.trim();
            if (allowedType.endsWith("/*")) {
                String prefix = allowedType.substring(0, allowedType.length() - 1);
                if (mimeType.startsWith(prefix)) {
                    return true;
                }
            } else if (mimeType.equals(allowedType)) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    public Resource getFileAsResource(String filePath) {
        try {
            String normalizedPath = filePath.trim().replaceAll("^/+", "").replaceAll("/+$", "");

            Path completePath = dataPath.resolve(normalizedPath).normalize();

            Resource resource = new UrlResource(completePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + filePath);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid file path: " + filePath, e);
        }
    }

    // Extract first and/or last frames from video for plagiarism detection
    public List<byte[]> extractVideoFrames(MultipartFile file, boolean extractFirst, boolean extractLast) {
        List<byte[]> frames = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputStream)) {

            grabber.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();

            int totalFrames = grabber.getLengthInFrames();
            log.debug("Video has {} frames", totalFrames);

            if (extractFirst) {
                grabber.setFrameNumber(0);
                Frame firstFrame = grabber.grabImage();
                if (firstFrame != null) {
                    BufferedImage firstImage = converter.convert(firstFrame);
                    byte[] firstBytes = bufferedImageToBytes(firstImage);
                    if (firstBytes != null) {
                        frames.add(firstBytes);
                        log.debug("Extracted first frame");
                    }
                }
            }

            if (extractLast && totalFrames > 1) {
                grabber.setFrameNumber(totalFrames - 1);
                Frame lastFrame = grabber.grabImage();
                if (lastFrame != null) {
                    BufferedImage lastImage = converter.convert(lastFrame);
                    byte[] lastBytes = bufferedImageToBytes(lastImage);
                    if (lastBytes != null) {
                        frames.add(lastBytes);
                        log.debug("Extracted last frame");
                    }
                }
            }

            grabber.stop();
            converter.close();

        } catch (Exception e) {
            log.error("Failed to extract video frames: {}", e.getMessage());
            throw new RuntimeException("Failed to extract video frames: " + e.getMessage());
        }

        return frames;
    }

    // Convert first and/or last PDF pages to images for plagiarism detection
    public List<byte[]> extractPdfPages(MultipartFile file, boolean extractFirst, boolean extractLast) {
        List<byte[]> pages = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {

            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            log.debug("PDF has {} pages", totalPages);

            if (extractFirst && totalPages > 0) {
                BufferedImage firstPage = renderer.renderImageWithDPI(0, 150);
                byte[] firstBytes = bufferedImageToBytes(firstPage);
                if (firstBytes != null) {
                    pages.add(firstBytes);
                    log.debug("Extracted first page");
                }
            }

            if (extractLast && totalPages > 1) {
                BufferedImage lastPage = renderer.renderImageWithDPI(totalPages - 1, 150);
                byte[] lastBytes = bufferedImageToBytes(lastPage);
                if (lastBytes != null) {
                    pages.add(lastBytes);
                    log.debug("Extracted last page");
                }
            }

        } catch (Exception e) {
            log.error("Failed to extract PDF pages: {}", e.getMessage());
            throw new RuntimeException("Failed to extract PDF pages: " + e.getMessage());
        }

        return pages;
    }

    public String extractDocxText(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String text = extractor.getText();
            log.debug("Extracted {} characters from DOCX", text.length());
            return text;

        } catch (Exception e) {
            log.error("Failed to extract DOCX text: {}", e.getMessage());
            throw new RuntimeException("Failed to extract DOCX text: " + e.getMessage());
        }
    }

    private byte[] bufferedImageToBytes(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to convert image to bytes: {}", e.getMessage());
            return null;
        }
    }

    // Split image into grid pieces for puzzle questions
    public PuzzleProcessingResult processPuzzleImage(MultipartFile imageFile, int gridRows, int gridCols, UUID questionId) {
        try {

            BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());
            if (originalImage == null) {
                throw new IllegalArgumentException("Invalid image file");
            }

            int imageWidth = originalImage.getWidth();
            int imageHeight = originalImage.getHeight();

            int pieceWidth = imageWidth / gridCols;
            int pieceHeight = imageHeight / gridRows;

            List<MultipartFile> originalImageList = Arrays.asList(imageFile);
            List<String> originalImagePaths = saveMedia(originalImageList, questionId, "Questions", Utils.Constants.SYSTEM_USER, null);
            String originalImagePath = originalImagePaths.get(0);
            String originalImageName = imageFile.getOriginalFilename();

            List<PuzzlePiece> pieces = new ArrayList<>();
            List<MultipartFile> pieceFiles = new ArrayList<>();
            int pieceNumber = 1;

            // Create puzzle pieces by dividing image into grid
            for (int row = 0; row < gridRows; row++) {
                for (int col = 0; col < gridCols; col++) {

                    int x = col * pieceWidth;
                    int y = row * pieceHeight;

                    BufferedImage piece = originalImage.getSubimage(x, y, pieceWidth, pieceHeight);

                    String pieceFileName = String.format("piece_%d_%d_%d.png", row, col, pieceNumber);
                    MultipartFile pieceFile = createMultipartFileFromBufferedImage(piece, pieceFileName);
                    pieceFiles.add(pieceFile);

                    PuzzlePiece puzzlePiece = new PuzzlePiece();
                    puzzlePiece.setId(pieceNumber);
                    puzzlePiece.setRow(row);
                    puzzlePiece.setCol(col);
                    puzzlePiece.setFileName(pieceFileName);
                    puzzlePiece.setCorrectPosition(pieceNumber);
                    puzzlePiece.setWidth(pieceWidth);
                    puzzlePiece.setHeight(pieceHeight);

                    pieces.add(puzzlePiece);
                    pieceNumber++;
                }
            }

            List<String> pieceFilePaths = saveMedia(pieceFiles, questionId, "Questions", Utils.Constants.SYSTEM_USER, null);

            for (int i = 0; i < pieces.size(); i++) {
                pieces.get(i).setFilePath(pieceFilePaths.get(i));
            }

            PuzzleProcessingResult result = new PuzzleProcessingResult();
            result.setSuccess(true);
            result.setOriginalImagePath(originalImagePath);
            result.setOriginalImageName(originalImageName);
            result.setPieces(pieces);
            result.setImageWidth(imageWidth);
            result.setImageHeight(imageHeight);
            result.setGridRows(gridRows);
            result.setGridCols(gridCols);
            result.setTotalPieces(pieces.size());

            log.info("Successfully processed puzzle image for question {}: {} pieces created",
                       questionId, pieces.size());

            return result;

        } catch (IOException e) {
            log.error("Error processing puzzle image for question {}: {}", questionId, e.getMessage());
            PuzzleProcessingResult result = new PuzzleProcessingResult();
            result.setSuccess(false);
            result.setErrorMessage("Failed to process image: " + e.getMessage());
            return result;
        }
    }

    // Convert BufferedImage to MultipartFile for saving
    private MultipartFile createMultipartFileFromBufferedImage(BufferedImage image, String fileName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return fileName;
            }

            @Override
            public String getContentType() {
                return "image/png";
            }

            @Override
            public boolean isEmpty() {
                return imageBytes.length == 0;
            }

            @Override
            public long getSize() {
                return imageBytes.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return imageBytes;
            }

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(imageBytes);
            }

            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                Files.write(dest.toPath(), imageBytes);
            }
        };
    }

    public static class PuzzlePiece {
        private int id;
        private int row;
        private int col;
        private String fileName;
        private String filePath;
        private int correctPosition;
        private int width;
        private int height;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public int getRow() { return row; }
        public void setRow(int row) { this.row = row; }

        public int getCol() { return col; }
        public void setCol(int col) { this.col = col; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public int getCorrectPosition() { return correctPosition; }
        public void setCorrectPosition(int correctPosition) { this.correctPosition = correctPosition; }

        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }

        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }

    public static class PuzzleProcessingResult {
        private boolean success;
        private String errorMessage;
        private String originalImagePath;
        private String originalImageName;
        private List<PuzzlePiece> pieces;
        private int imageWidth;
        private int imageHeight;
        private int gridRows;
        private int gridCols;
        private int totalPieces;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getOriginalImagePath() { return originalImagePath; }
        public void setOriginalImagePath(String originalImagePath) { this.originalImagePath = originalImagePath; }

        public String getOriginalImageName() { return originalImageName; }
        public void setOriginalImageName(String originalImageName) { this.originalImageName = originalImageName; }

        public List<PuzzlePiece> getPieces() { return pieces; }
        public void setPieces(List<PuzzlePiece> pieces) { this.pieces = pieces; }

        public int getImageWidth() { return imageWidth; }
        public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }

        public int getImageHeight() { return imageHeight; }
        public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }

        public int getGridRows() { return gridRows; }
        public void setGridRows(int gridRows) { this.gridRows = gridRows; }

        public int getGridCols() { return gridCols; }
        public void setGridCols(int gridCols) { this.gridCols = gridCols; }

        public int getTotalPieces() { return totalPieces; }
        public void setTotalPieces(int totalPieces) { this.totalPieces = totalPieces; }
    }

}
