package org.codinjutsu.tools.jenkins.logic.github;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocalGitContextTest {

    private static final Set<String> GITHUB_HOSTS = Set.of("github.com");
    private static final Set<String> ENTERPRISE_HOSTS = Set.of("github.example.com");

    @Test
    void parsesSshUrl() {
        final Optional<LocalGitContext.OwnerRepo> parsed =
                LocalGitContext.parseOwnerRepo("git@github.com:acme/widget.git", GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void parsesSshUrlWithoutGitSuffix() {
        final Optional<LocalGitContext.OwnerRepo> parsed =
                LocalGitContext.parseOwnerRepo("git@github.com:acme/widget", GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void parsesHttpsUrl() {
        final Optional<LocalGitContext.OwnerRepo> parsed =
                LocalGitContext.parseOwnerRepo("https://github.com/acme/widget.git", GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void parsesHttpsUrlWithUserInfoAndNoGitSuffix() {
        final Optional<LocalGitContext.OwnerRepo> parsed =
                LocalGitContext.parseOwnerRepo("https://joe@github.com/acme/widget", GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void parsesSshSchemeUrl() {
        final Optional<LocalGitContext.OwnerRepo> parsed =
                LocalGitContext.parseOwnerRepo("ssh://git@github.com/acme/widget.git", GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void parsesEnterpriseHost() {
        final Optional<LocalGitContext.OwnerRepo> parsed = LocalGitContext.parseOwnerRepo(
                "https://github.example.com/acme/widget.git", ENTERPRISE_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void ignoresNonGithubHost() {
        final Optional<LocalGitContext.OwnerRepo> parsed =
                LocalGitContext.parseOwnerRepo("git@gitlab.com:acme/widget.git", GITHUB_HOSTS);

        assertThat(parsed).isEmpty();
    }

    @Test
    void hostsForDefaultsToGithubCom() {
        assertThat(LocalGitContext.hostsFor("https://api.github.com")).containsExactly("github.com");
        assertThat(LocalGitContext.hostsFor("")).containsExactly("github.com");
    }

    @Test
    void hostsForEnterpriseStripsApiPrefix() {
        assertThat(LocalGitContext.hostsFor("https://api.github.example.com"))
                .containsExactly("github.example.com");
    }

    @Test
    void isGithubUrlRespectsHosts() {
        assertThat(LocalGitContext.isGithubUrl("https://github.com/x/y.git", GITHUB_HOSTS)).isTrue();
        assertThat(LocalGitContext.isGithubUrl("https://gitlab.com/x/y.git", GITHUB_HOSTS)).isFalse();
    }
}
