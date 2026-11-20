package com.career.recommendation.service;

import com.career.recommendation.exception.InvalidProofFileException;
import com.career.recommendation.exception.ProofStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
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

    /**
     * 함수 이름: store()
     * 기능: 업로드된 증빙 이미지를 검증(비어 있음·5MB 초과·시그니처)한 뒤 UUID 파일명으로 로컬 저장소에 저장한다.
     * 파라미터: MultipartFile file -> 업로드된 증빙 이미지 파일
     * 반환값: StoredProof -> 원본 파일명(정제)·저장 파일명·콘텐츠 타입·크기
     * 예외: InvalidProofFileException -> 파일이 없거나 크기·형식·경로가 올바르지 않을 때
     *       ProofStorageException -> 저장 중 IO 오류가 발생했을 때
     */
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

    /**
     * 함수 이름: deleteQuietly()
     * 기능: 파일을 지운다. 예외를 던지지 않는다 — DB 저장 실패 뒤의 보상 동작에서 원래 예외를 가리지 않기 위함.
     * 파라미터: String storedName -> 삭제할 저장 파일명(null·공백이면 지울 것이 없으므로 true)
     * 반환값: boolean -> 파일이 지워졌거나 원래 없었으면 true, IO 오류/경로 이상으로 남아 있으면 false
     *         (ProofRetentionScheduler는 false면 DB 메타를 비우지 않아 고아 파일이 생기지 않게 한다)
     */
    public boolean deleteQuietly(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return true;
        }

        try {
            Path target = storageRoot.resolve(storedName).normalize();
            ensureInsideStorageRoot(target);
            Files.deleteIfExists(target);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 함수 이름: load()
     * 기능: 검수자가 증빙을 열어볼 때 쓴다. 저장 루트 밖 경로·없는 파일은 Optional.empty().
     * 파일이 없을 수 있는 이유: 서버 재배포로 로컬 디스크가 초기화된 경우(E2-4 S3 전환 전까지의 한계).
     * 파라미터: String storedName -> 읽을 저장 파일명
     * 반환값: Optional<Resource> -> 파일 리소스(없거나 경로가 올바르지 않으면 빈 Optional)
     */
    public Optional<Resource> load(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return Optional.empty();
        }
        try {
            Path target = resolveForInspection(storedName);
            return Files.isRegularFile(target) ? Optional.of(new FileSystemResource(target)) : Optional.empty();
        } catch (InvalidProofFileException outsideRoot) {
            return Optional.empty();
        }
    }

    /**
     * 함수 이름: resolveForInspection()
     * 기능: 저장 파일명을 저장 루트 아래의 절대 경로로 변환하고, 루트 밖이면 예외를 던진다.
     * 파라미터: String storedName -> 저장 파일명
     * 반환값: Path -> 정규화된 저장 파일 경로
     * 예외: InvalidProofFileException -> 경로가 저장 루트 밖일 때
     */
    Path resolveForInspection(String storedName) {
        Path target = storageRoot.resolve(storedName).normalize();
        ensureInsideStorageRoot(target);
        return target;
    }

    /**
     * 함수 이름: validateBasicProperties()
     * 기능: 업로드 파일이 존재하고 비어 있지 않은지, 최대 크기(5MB) 이하인지 검사한다.
     * 파라미터: MultipartFile file -> 검사할 업로드 파일
     * 반환값: 없음
     * 예외: InvalidProofFileException -> 파일이 없거나 비어 있거나 5MB를 초과할 때
     */
    private void validateBasicProperties(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProofFileException("합격 증빙 이미지가 필요합니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidProofFileException("증빙 이미지는 5MB 이하만 업로드할 수 있습니다.");
        }
    }

    /**
     * 함수 이름: detectImage()
     * 기능: 파일 앞 12바이트(매직 넘버)를 읽어 PNG·JPEG·WebP 중 어느 형식인지 판별한다. 확장자·Content-Type은 신뢰하지 않는다.
     * 파라미터: MultipartFile file -> 판별할 업로드 파일
     * 반환값: DetectedImage -> 판별된 확장자와 콘텐츠 타입
     * 예외: InvalidProofFileException -> 지원하지 않는 형식일 때
     *       IOException -> 파일을 읽지 못했을 때
     */
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

    /**
     * 함수 이름: isPng()
     * 기능: 헤더가 PNG 시그니처(8바이트)로 시작하는지 확인한다.
     * 파라미터: byte[] header -> 파일 앞부분 바이트
     * 반환값: boolean -> PNG이면 true
     */
    private boolean isPng(byte[] header) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        return startsWith(header, signature);
    }

    /**
     * 함수 이름: isJpeg()
     * 기능: 헤더가 JPEG 시그니처(FF D8 FF)로 시작하는지 확인한다.
     * 파라미터: byte[] header -> 파일 앞부분 바이트
     * 반환값: boolean -> JPEG이면 true
     */
    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF;
    }

    /**
     * 함수 이름: isWebp()
     * 기능: 헤더의 0~3바이트가 "RIFF", 8~11바이트가 "WEBP"인지 확인한다.
     * 파라미터: byte[] header -> 파일 앞부분 바이트(12바이트 이상이어야 함)
     * 반환값: boolean -> WebP이면 true
     */
    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
    }

    /**
     * 함수 이름: startsWith()
     * 기능: 바이트 배열이 주어진 접두 바이트열로 시작하는지 비교한다.
     * 파라미터: byte[] bytes -> 검사 대상 바이트 배열
     *           byte[] prefix -> 기대하는 접두 바이트열
     * 반환값: boolean -> 접두가 일치하면 true(대상이 더 짧으면 false)
     */
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

    /**
     * 함수 이름: sanitizeOriginalName()
     * 기능: 원본 파일명에서 경로 구분자 앞부분을 제거해 파일명만 남기고 255자로 자른다. 비어 있으면 "proof-image"를 쓴다.
     * 파라미터: String originalName -> 업로드 시 원본 파일명(null 허용)
     * 반환값: String -> 정제된 파일명
     */
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

    /**
     * 함수 이름: ensureInsideStorageRoot()
     * 기능: 대상 경로의 부모 디렉터리가 저장 루트와 정확히 같은지 확인해 경로 탈출(path traversal)을 막는다.
     * 파라미터: Path target -> 검사할 정규화된 대상 경로
     * 반환값: 없음
     * 예외: InvalidProofFileException -> 저장 루트 밖 경로일 때
     */
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
