package com.career.recommendation.service;

import com.career.recommendation.dto.user.LanguageScoreRequest;
import com.career.recommendation.dto.user.UserSpecRequest;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.UserSpecRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSpecServiceTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserSpecRepository userSpecRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserSpecService userSpecService;

    @Test
    void 최초_저장시_DTO를_Entity_형식으로_변환한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        UserSpecRequest request = createRequest();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userSpecRepository.save(any(UserSpec.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userSpecService.saveOrUpdateMySpec(authentication, request);

        ArgumentCaptor<UserSpec> captor = ArgumentCaptor.forClass(UserSpec.class);
        verify(userSpecRepository).save(captor.capture());

        UserSpec savedUserSpec = captor.getValue();
        assertThat(savedUserSpec.getUser()).isSameAs(user);
        assertThat(savedUserSpec.getGpa()).isEqualByComparingTo("3.8");
        assertThat(savedUserSpec.getGpaMax()).isEqualByComparingTo("4.5");
        assertThat(savedUserSpec.getGrade()).isEqualTo(3);
        assertThat(savedUserSpec.getLanguageScores()).containsExactly(
                Map.of("type", "TOEIC", "score", 850, "maxScore", 990),
                Map.of("type", "OPIC", "grade", "IH")
        );
        assertThat(savedUserSpec.getCertifications())
                .containsExactly("SQLD", "정보처리기사");
    }

    @Test
    void 자격증의_앞뒤공백과_중복을_제거한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        UserSpecRequest request = createRequest();
        request.setCertifications(List.of(" SQLD ", "SQLD", " 정보처리기사 "));

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userSpecRepository.save(any(UserSpec.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userSpecService.saveOrUpdateMySpec(authentication, request);

        ArgumentCaptor<UserSpec> captor = ArgumentCaptor.forClass(UserSpec.class);
        verify(userSpecRepository).save(captor.capture());

        assertThat(captor.getValue().getCertifications())
                .containsExactly("SQLD", "정보처리기사");
    }

    @Test
    void 기존_UserSpec이_있으면_새로_생성하지_않고_기존_Entity를_수정한다() {
        UUID userId = UUID.randomUUID();
        UUID userSpecId = UUID.randomUUID();
        User user = createUser(userId);
        UserSpec existingUserSpec = UserSpec.builder()
                .id(userSpecId)
                .user(user)
                .gpa(new BigDecimal("3.0"))
                .gpaMax(new BigDecimal("4.5"))
                .grade(2)
                .build();
        UserSpecRequest request = createRequest();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.of(existingUserSpec));
        when(userSpecRepository.save(any(UserSpec.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userSpecService.saveOrUpdateMySpec(authentication, request);

        verify(userSpecRepository).save(same(existingUserSpec));
        assertThat(existingUserSpec.getId()).isEqualTo(userSpecId);
        assertThat(existingUserSpec.getGpa()).isEqualByComparingTo("3.8");
        assertThat(existingUserSpec.getGrade()).isEqualTo(3);
    }

    @Test
    void 빈_목록은_null이_아닌_빈_배열로_저장한다() {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId);
        UserSpecRequest request = createRequest();
        request.setLanguageScores(List.of());
        request.setCertifications(List.of());

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userSpecRepository.save(any(UserSpec.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userSpecService.saveOrUpdateMySpec(authentication, request);

        ArgumentCaptor<UserSpec> captor = ArgumentCaptor.forClass(UserSpec.class);
        verify(userSpecRepository).save(captor.capture());

        UserSpec savedUserSpec = captor.getValue();
        assertThat(savedUserSpec.getLanguageScores()).isEmpty();
        assertThat(savedUserSpec.getCertifications()).isEmpty();
    }

    private User createUser(UUID userId) {
        return User.builder()
                .id(userId)
                .provider("KAKAO")
                .providerId("provider-id")
                .nickname("테스터")
                .build();
    }

    private UserSpecRequest createRequest() {
        UserSpecRequest request = new UserSpecRequest();
        request.setGpa(new BigDecimal("3.8"));
        request.setGpaMax(new BigDecimal("4.5"));
        request.setGrade(3);
        request.setLanguageScores(List.of(toeic(850), opic("IH")));
        request.setCertifications(List.of("SQLD", "정보처리기사"));
        return request;
    }

    private LanguageScoreRequest toeic(int score) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("TOEIC");
        request.setScore(score);
        request.setMaxScore(990);
        return request;
    }

    private LanguageScoreRequest opic(String grade) {
        LanguageScoreRequest request = new LanguageScoreRequest();
        request.setType("OPIC");
        request.setGrade(grade);
        return request;
    }
}
