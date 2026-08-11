package com.career.recommendation.service;

import com.career.recommendation.exception.InvalidProofFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalProofStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void png_증빙을_UUID_파일명으로_저장한다() throws Exception {
        LocalProofStorageService service = new LocalProofStorageService(tempDirectory.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "합격 메일.png",
                "image/png",
                pngBytes()
        );

        LocalProofStorageService.StoredProof stored = service.store(proof);

        assertThat(stored.originalName()).isEqualTo("합격 메일.png");
        assertThat(stored.storedName()).endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.fileSize()).isEqualTo(pngBytes().length);
        assertThat(Files.exists(tempDirectory.resolve(stored.storedName()))).isTrue();
    }

    @Test
    void 확장자만_이미지인_가짜_파일은_거부한다() {
        LocalProofStorageService service = new LocalProofStorageService(tempDirectory.toString());
        MockMultipartFile fakeImage = new MockMultipartFile(
                "proof",
                "fake.png",
                "image/png",
                "not-an-image".getBytes()
        );

        assertThatThrownBy(() -> service.store(fakeImage))
                .isInstanceOf(InvalidProofFileException.class)
                .hasMessageContaining("JPEG, PNG, WebP");
    }

    @Test
    void 저장된_증빙을_정리할_수_있다() throws Exception {
        LocalProofStorageService service = new LocalProofStorageService(tempDirectory.toString());
        LocalProofStorageService.StoredProof stored = service.store(new MockMultipartFile(
                "proof", "accepted.png", "image/png", pngBytes()
        ));

        service.deleteQuietly(stored.storedName());

        assertThat(Files.exists(tempDirectory.resolve(stored.storedName()))).isFalse();
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
    }
}
