package com.career.recommendation.util;

import com.career.recommendation.exception.GithubUsernameInvalidException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubUsernameParserTest {

    @Test
    void 순수_username은_그대로_반환한다() {
        assertThat(GithubUsernameParser.parse("octocat")).isEqualTo("octocat");
    }

    @Test
    void github_URL에서_username만_추출한다() {
        assertThat(GithubUsernameParser.parse("https://github.com/octocat")).isEqualTo("octocat");
        assertThat(GithubUsernameParser.parse("github.com/octocat")).isEqualTo("octocat");
        assertThat(GithubUsernameParser.parse("http://www.github.com/octocat/")).isEqualTo("octocat");
        assertThat(GithubUsernameParser.parse("https://github.com/octocat/some-repo")).isEqualTo("octocat");
    }

    @Test
    void 하이픈으로_시작하거나_끝나면_거부한다() {
        assertThatThrownBy(() -> GithubUsernameParser.parse("-octocat"))
                .isInstanceOf(GithubUsernameInvalidException.class);
        assertThatThrownBy(() -> GithubUsernameParser.parse("octocat-"))
                .isInstanceOf(GithubUsernameInvalidException.class);
    }

    @Test
    void 하이픈이_연속되면_거부한다() {
        assertThatThrownBy(() -> GithubUsernameParser.parse("octo--cat"))
                .isInstanceOf(GithubUsernameInvalidException.class);
    }

    @Test
    void 길이가_39자를_초과하면_거부한다() {
        String tooLong = "a".repeat(40);
        assertThatThrownBy(() -> GithubUsernameParser.parse(tooLong))
                .isInstanceOf(GithubUsernameInvalidException.class);
    }

    @Test
    void 빈_입력은_거부한다() {
        assertThatThrownBy(() -> GithubUsernameParser.parse("  "))
                .isInstanceOf(GithubUsernameInvalidException.class);
    }
}
