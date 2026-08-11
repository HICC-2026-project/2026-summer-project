package com.career.recommendation.service;

import com.career.recommendation.exception.InvalidProofFileException;
import com.career.recommendation.exception.ProofStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalProofStorageService {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final Path storageRoot;

    public LocalProofStorageService(
            @Value("${app.storage.passer-proof-directory:./uploads/passer-proofs}") String storageDirectory
    ) {
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredProof store(MultipartFile file) {
        validateBasicProperties(file);

        try {
            Files.createDirectories(storageRoot);

            DetectedImage detectedImage = detectImage(file);
            String storedName = UUID.randomUUID() + detectedImage.extension();
            Path target = storageRoot.resolve(storedName).normalize();
            ensureInsideStorageRoot(target);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredProof(
                    sanitizeOriginalName(file.getOriginalFilename()),
                    storedName,
                    detectedImage.contentType(),
                    file.getSize()
            );
        } catch (InvalidProofFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ProofStorageException("증빙자료를 로컬 저장소에 저장하지 못했습니다.", exception);
        }
    }

    public void deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }

        try {
            Path target = storageRoot.resolve(storedName).normalize();
            ensureInsideStorageRoot(target);
            Files.deleteIfExists(target);
        } catch (IOException | RuntimeException ignored) {
            // DB 저장 실패 뒤 정리하는 보상 동작이다. 원래 예외를 가리지 않도록 무시한다.
        }
    }

    Path resolveForInspection(String storedName) {
        Path target = storageRoot.resolve(storedName).normalize();
        ensureInsideStorageRoot(target);
        return target;
    }

    private void validateBasicProperties(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProofFileException("합격 증빙 이미지가 필요합니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidProofFileException("증빙 이미지는 5MB 이하만 업로드할 수 있습니다.");
        }
    }

    private DetectedImage detectImage(MultipartFile file) throws IOException {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(12);
        }

        if (isPng(header)) {
            return new DetectedImage(".png", "image/png");
        }
        if (isJpeg(header)) {
            return new DetectedImage(".jpg", "image/jpeg");
        }
        if (isWebp(header)) {
            return new DetectedImage(".webp", "image/webp");
        }
        throw new InvalidProofFileException("JPEG, PNG, WebP 이미지 파일만 업로드할 수 있습니다.");
    }

    private boolean isPng(byte[] header) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return startsWith(header, signature);
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String sanitizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "proof-image";
        }
        String normalized = originalName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank()) {
            return "proof-image";
        }
        return fileName.substring(0, Math.min(fileName.length(), 255));
    }

    private void ensureInsideStorageRoot(Path target) {
        if (!target.getParent().equals(storageRoot)) {
            throw new InvalidProofFileException("올바르지 않은 증빙자료 저장 경로입니다.");
        }
    }

    public record StoredProof(
            String originalName,
            String storedName,
            String contentType,
            long fileSize
    ) {
    }

    private record DetectedImage(String extension, String contentType) {
        private DetectedImage {
            extension = extension.toLowerCase(Locale.ROOT);
        }
    }
}
