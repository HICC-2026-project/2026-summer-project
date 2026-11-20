package com.career.recommendation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * E3(1단계) GitHub 분석(@Async)을 위한 전용 소형 실행기. 서비스 전체에 @Async 사용처가 이
 * 기능 하나뿐이라 기본 SimpleAsyncTaskExecutor(스레드 무한 생성) 대신, 외부 API를 호출하는
 * 백그라운드 작업 특성에 맞게 스레드 수를 작게 제한한다 — GitHub 레이트리밋(시간당 60~5000회)
 * 자체가 병목이라 동시에 여러 스레드를 둘 이유가 없고, 무제한 큐/스레드는 동시 분석 요청이
 * 몰릴 때 커넥션 폭주로 이어질 수 있다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 함수 이름: githubTaskExecutor()
     * 기능: GitHub 분석 @Async 작업 전용 스레드 풀 빈("githubTaskExecutor")을 만든다. 코어 1·최대 2
     *       스레드, 큐 20, 스레드 이름 접두어 "github-"로 제한하고, 종료 시 진행 중 작업을 최대 10초
     *       기다린다.
     * 파라미터: 없음
     * 반환값: 초기화된 ThreadPoolTaskExecutor(Executor)
     */
    @Bean(name = "githubTaskExecutor")
    public Executor githubTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("github-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
