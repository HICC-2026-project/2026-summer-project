package com.career.recommendation.util;

import com.career.recommendation.domain.JobType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3(1단계) 골든 테스트 — JobSignalClassifier의 결정적 규칙을 대표 시나리오로 고정한다.
 */
class JobSignalClassifierTest {

    @Test
    void Spring_레포는_BACKEND로_분류된다() {
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "career-recommendation-service",
                "Spring Boot 기반 커리어 추천 서비스",
                Map.of("Java", 120_000L),
                List.of(
                        "src/main/java/com/career/controller/UserController.java",
                        "src/main/java/com/career/service/UserService.java",
                        "src/main/java/com/career/repository/UserRepository.java",
                        "pom.xml"
                ),
                List.of("spring-boot-starter-web", "spring-boot-starter-data-jpa"),
                80,
                6
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isEqualTo(JobType.BACKEND);
    }

    @Test
    void React_레포는_FRONTEND로_분류된다() {
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "career-frontend",
                "React 기반 프론트엔드",
                Map.of("TypeScript", 90_000L, "CSS", 5_000L),
                List.of(
                        "src/components/Header.tsx",
                        "src/pages/Home.tsx",
                        "src/hooks/useAuth.tsx",
                        "src/styles/global.css",
                        "package.json"
                ),
                List.of("react", "react-dom", "next", "tailwindcss"),
                50,
                5
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isEqualTo(JobType.FRONTEND);
    }

    @Test
    void 보안_경로와_spring_security_의존성은_BACKEND보다_SECURITY_가중이_높다() {
        // 언어는 Java(BACKEND 신호)지만, 보안 경로 3곳 + SecurityConfig 파일 + spring-security
        // 의존성이 겹치면 SECURITY 점수가 더 높아야 한다(BACKEND로 뭉개지면 안 된다).
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "auth-server",
                "JWT 기반 인증 서버",
                Map.of("Java", 50_000L),
                List.of(
                        "src/main/java/com/example/security/SecurityConfig.java",
                        "src/main/java/com/example/auth/JwtTokenProvider.java",
                        "src/main/java/com/example/oauth/OAuth2Handler.java"
                ),
                List.of("spring-security", "jjwt-api"),
                40,
                4
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isEqualTo(JobType.SECURITY);
    }

    @Test
    void ipynb와_torch_의존성은_AI_ML로_분류된다() {
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "image-classifier",
                "PyTorch 이미지 분류 실험",
                Map.of("Jupyter Notebook", 30_000L, "Python", 5_000L),
                List.of("notebooks/train.ipynb", "notebooks/eval.ipynb", "requirements.txt"),
                List.of("torch", "torchvision"),
                20,
                3
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isEqualTo(JobType.AI_ML);
    }

    @Test
    void Dockerfile과_workflows만_있는_레포는_BACKEND_보조_가중만_받는다() {
        // 다른 직무 신호가 전혀 없으므로 인프라 보조 가중(BACKEND ×0.5)만으로도 OTHER보다는
        // BACKEND가 되어야 한다(다른 카테고리는 전부 0점).
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "infra-scripts",
                "배포 스크립트 모음",
                Map.of("Shell", 1_000L),
                List.of("Dockerfile", "docker-compose.yml", ".github/workflows/deploy.yml"),
                List.of(),
                5,
                2
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isEqualTo(JobType.BACKEND);
    }

    @Test
    void lock_파일과_min_파일은_노이즈로_제외되어_신호에_기여하지_않는다() {
        // package-lock.json에 등장하는 "react" 문자열이나 확장자 신호에 절대 걸리지 않아야 한다.
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "empty-signal-repo",
                null,
                Map.of(),
                List.of("package-lock.json", "yarn.lock", "dist/app.min.js", "build/output.min.css"),
                List.of(),
                3,
                1
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isNull(); // OTHER
    }

    @Test
    void 짧은_키워드는_경로_세그먼트_토큰과_정확히_일치할_때만_매칭된다() {
        // 아래 폴더명은 각각 실제 키워드("api","app","acl","etl","vue")를 부분문자열로 포함하지만
        // 세그먼트 토큰 전체와는 다르다 — 부분문자열만으로 오매칭되면 안 된다.
        //   capital ⊃ api, happy ⊃ app, spectacle ⊃ acl, beetle ⊃ etl, revue ⊃ vue
        JobSignalClassifier.RepoSignal repo = new JobSignalClassifier.RepoSignal(
                "misc-utils",
                "여러 유틸리티 모음",
                Map.of("JavaScript", 10_000L),
                List.of(
                        "src/capital/CapitalCalculator.js",
                        "src/happy-hour/Discount.js",
                        "src/spectacle/Viewer.js",
                        "src/beetle-tracker/Tracker.js",
                        "src/revue/Magazine.js"
                ),
                List.of(),
                10,
                2
        );

        JobSignalClassifier.RepoClassification result = JobSignalClassifier.classifyRepo(repo);

        assertThat(result.primaryJob()).isNull(); // OTHER — 부분문자열 오매칭 없음
    }

    @Test
    void 사용자_전체_비율은_커밋_파일_활동개월_가중으로_정규화된다() {
        JobSignalClassifier.RepoSignal backendRepo = new JobSignalClassifier.RepoSignal(
                "backend-repo", null, Map.of("Java", 1000L),
                List.of("src/main/java/com/example/controller/A.java"),
                List.of("spring-boot"), 80, 8
        );
        JobSignalClassifier.RepoSignal frontendRepo = new JobSignalClassifier.RepoSignal(
                "frontend-repo", null, Map.of(),
                List.of("src/components/A.tsx"),
                List.of("react"), 20, 2
        );

        JobSignalClassifier.ClassificationResult result =
                JobSignalClassifier.classify(List.of(backendRepo, frontendRepo));

        Map<String, Double> ratios = result.userRatios();
        assertThat(ratios.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(ratios.get("BACKEND")).isGreaterThan(ratios.get("FRONTEND"));
        assertThat(ratios).containsKeys("BACKEND", "FRONTEND", "SECURITY", "AI_ML", "DATA_ENGINEER", "PM", "OTHER");
    }

    @Test
    void 레포가_없으면_모든_비율이_0이다() {
        JobSignalClassifier.ClassificationResult result = JobSignalClassifier.classify(List.of());

        assertThat(result.repos()).isEmpty();
        assertThat(result.userRatios()).isEmpty();
    }
}
