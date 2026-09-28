package com.career.recommendation.service;

import com.career.recommendation.dto.admin.AdminPasserEntryRequest;
import com.career.recommendation.dto.admin.AdminPasserEntryResponse;
import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasserEntryServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private PasserDataRepository passerDataRepository;
    @Mock private JobSpecProfileService jobSpecProfileService;
    @Mock private Authentication authentication;

    @InjectMocks
    private AdminPasserEntryService service;

    private final User admin = User.builder().id(UUID.randomUUID()).provider("KAKAO").providerId("admin").role("ADMIN").build();

    @Test
    void 등록하면_ADMIN_ENTRY_출처로_검수없이_바로_검증완료_상태가_된다() {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(admin);
        when(passerDataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPasserEntryResponse response = service.create(authentication, validRequest());

        ArgumentCaptor<PasserData> captor = ArgumentCaptor.forClass(PasserData.class);
        verify(passerDataRepository).save(captor.capture());
        PasserData saved = captor.getValue();

        assertThat(saved.getDataOrigin()).isEqualTo(PasserData.ORIGIN_ADMIN_ENTRY);
        assertThat(saved.getIsVerified()).isTrue();
        assertThat(saved.getReviewedBy()).isSameAs(admin);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getReporter()).isNull();
        assertThat(saved.getSourceNote()).isEqualTo("https://blog.example.com/passed-2026");
        assertThat(response.getStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void 저장_후_직무_프로필_캐시를_무효화한다() {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(admin);
        when(passerDataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(authentication, validRequest());

        verify(jobSpecProfileService).evictAll();
    }

    @Test
    void 출처_메모의_앞뒤_공백은_저장_전에_제거한다() {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(admin);
        when(passerDataRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminPasserEntryRequest request = validRequest();
        request.setSourceNote("  https://blog.example.com/passed-2026  ");

        service.create(authentication, request);

        ArgumentCaptor<PasserData> captor = ArgumentCaptor.forClass(PasserData.class);
        verify(passerDataRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceNote()).isEqualTo("https://blog.example.com/passed-2026");
    }

    private AdminPasserEntryRequest validRequest() {
        AdminPasserEntryRequest request = new AdminPasserEntryRequest();
        request.setJobType("BACKEND");
        request.setYear(2026);
        request.setGpa(new BigDecimal("3.8"));
        request.setGpaMax(new BigDecimal("4.5"));
        request.setLanguageScores(List.of(toeic(850)));
        request.setCertifications(List.of("정보처리기사"));
        request.setExperienceCount(2);
        request.setSourceNote("https://blog.example.com/passed-2026");
        return request;
    }

    private LanguageScoreRequest toeic(int score) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("TOEIC");
        request.setScore(score);
        request.setMaxScore(990);
        return request;
    }
}
