package com.career.recommendation.security;

import com.career.recommendation.entity.User;
import com.career.recommendation.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;

    private static final String PROTECTED_PATH = "/api/v1/some-protected-resource";

    @Test
    void 토큰_없이_보호된_경로에_접근하면_401() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 위조된_토큰으로_보호된_경로에_접근하면_401() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 유효한_토큰이면_인증을_통과해서_컨트롤러_라우팅_단계까지_간다() throws Exception {
        User user = userRepository.save(User.builder()
                .email("filter-test-" + System.nanoTime() + "@example.com")
                .nickname("tester")
                .provider("KAKAO")
                .providerId("provider-id-" + System.nanoTime())
                .build());
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());

        // 인증은 통과해야 하므로 401이 아니어야 한다 (해당 경로엔 컨트롤러가 없어 404가 정상)
        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void public_경로는_토큰_없이도_접근된다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/refresh"))
                .andExpect(status().is(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED.value()));
    }
}
