package com.career.recommendation.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.career.recommendation.exception.GithubUserNotFoundException;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * E11-5 요구사항 "아이디는 로그에 절대 남기지 않는다" — GithubClient가 던지는
 * GithubUserNotFoundException 등의 예외 메시지엔 username이 그대로 담겨 있어(예:
 * "존재하지 않는 GitHub 계정입니다: secret-user123"), 예외 객체나 메시지를 그대로 로그에 넘기면
 * 이 마스킹 원칙이 깨진다. Logback ListAppender로 이 클래스가 실제로 남긴 로그 전부를 캡처해
 * 아이디 원문이 단 한 줄도 없는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PasserGithubContributionRunnerLogMaskingTest {

    private static final String SECRET_USERNAME = "secret-user123";

    @Mock
    private GithubClient githubClient;
    @Mock
    private PasserDataRepository passerDataRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private PasserGithubContributionRunner runner;

    private ListAppender<ILoggingEvent> appender;
    private Logger runnerLogger;

    @BeforeEach
    void attachAppender() {
        runnerLogger = (Logger) LoggerFactory.getLogger(PasserGithubContributionRunner.class);
        appender = new ListAppender<>();
        appender.start();
        runnerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        runnerLogger.detachAppender(appender);
    }

    @Test
    void 존재하지_않는_계정_예외가_나도_로그에_아이디가_남지_않는다() {
        when(githubClient.analyze(any(), any())).thenThrow(new GithubUserNotFoundException(SECRET_USERNAME));

        runner.analyze(UUID.randomUUID(), SECRET_USERNAME, 2026);

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET_USERNAME);
            if (event.getThrowableProxy() != null) {
                assertThat(event.getThrowableProxy().getMessage()).doesNotContain(SECRET_USERNAME);
            }
        }
    }

    @Test
    void 알수없는_런타임_예외가_나도_로그에_아이디가_남지_않는다() {
        when(githubClient.analyze(any(), any()))
                .thenThrow(new RuntimeException("boom near user " + SECRET_USERNAME));

        runner.analyze(UUID.randomUUID(), SECRET_USERNAME, 2026);

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET_USERNAME);
            if (event.getThrowableProxy() != null) {
                assertThat(event.getThrowableProxy().getMessage()).doesNotContain(SECRET_USERNAME);
            }
        }
    }
}
