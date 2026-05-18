package org.codinjutsu.tools.jenkins.logic.github;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JenkinsJobMatcherTest {

    private static final Set<String> GITHUB_HOSTS = Set.of("github.com");

    @Test
    void extractsOwnerRepoFromGitHubSCMSource() throws IOException {
        final String xml = readResource("/jenkins/github/multibranch-githubscmsource.xml");

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void extractsOwnerRepoFromGitSCMUserRemoteConfigs() throws IOException {
        final String xml = readResource("/jenkins/github/pipeline-gitscm.xml");

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void ignoresNonGithubScm() throws IOException {
        final String xml = readResource("/jenkins/github/non-github-scm.xml");

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, GITHUB_HOSTS);

        assertThat(parsed).isEmpty();
    }

    @Test
    void extractsOwnerRepoFromGitHubSCMSourceWithTraitsAndNoRepositoryUrl() throws IOException {
        final String xml = readResource("/jenkins/github/multibranch-githubscmsource-traits.xml");

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void extractsOwnerRepoFromRepositoryUrl() {
        final String xml = """
                <?xml version='1.1' encoding='UTF-8'?>
                <org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
                  <sources class="jenkins.branch.MultiBranchProject$BranchSourceList">
                    <data>
                      <jenkins.branch.BranchSource>
                        <source class="org.jenkinsci.plugins.github_branch_source.GitHubSCMSource">
                          <repositoryUrl>https://github.com/acme/widget</repositoryUrl>
                        </source>
                      </jenkins.branch.BranchSource>
                    </data>
                  </sources>
                </org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
                """;

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, GITHUB_HOSTS);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void rejectsGitHubSCMSourceWhenApiUriDoesNotMatchConfiguredHosts() {
        final String xml = """
                <?xml version='1.1' encoding='UTF-8'?>
                <org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
                  <sources class="jenkins.branch.MultiBranchProject$BranchSourceList">
                    <data>
                      <jenkins.branch.BranchSource>
                        <source class="org.jenkinsci.plugins.github_branch_source.GitHubSCMSource">
                          <apiUri>https://api.github.enterprise.example.com</apiUri>
                          <repoOwner>acme</repoOwner>
                          <repository>widget</repository>
                        </source>
                      </jenkins.branch.BranchSource>
                    </data>
                  </sources>
                </org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
                """;

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, GITHUB_HOSTS);

        assertThat(parsed).isEmpty();
    }

    @Test
    void acceptsGitHubSCMSourceForEnterpriseWhenApiUriMatchesConfiguredHosts() {
        final String xml = """
                <?xml version='1.1' encoding='UTF-8'?>
                <org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
                  <sources class="jenkins.branch.MultiBranchProject$BranchSourceList">
                    <data>
                      <jenkins.branch.BranchSource>
                        <source class="org.jenkinsci.plugins.github_branch_source.GitHubSCMSource">
                          <apiUri>https://api.github.enterprise.example.com</apiUri>
                          <repoOwner>acme</repoOwner>
                          <repository>widget</repository>
                        </source>
                      </jenkins.branch.BranchSource>
                    </data>
                  </sources>
                </org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
                """;
        final Set<String> enterpriseHosts = Set.of("github.enterprise.example.com");

        final Optional<LocalGitContext.OwnerRepo> parsed =
                JenkinsJobMatcher.extractOwnerRepoFromConfigXml(xml, enterpriseHosts);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().owner()).isEqualTo("acme");
        assertThat(parsed.get().repo()).isEqualTo("widget");
    }

    @Test
    void blankXmlReturnsEmpty() {
        assertThat(JenkinsJobMatcher.extractOwnerRepoFromConfigXml("", GITHUB_HOSTS)).isEmpty();
    }

    @Test
    void unparseableXmlReturnsEmpty() {
        assertThat(JenkinsJobMatcher.extractOwnerRepoFromConfigXml("<not really xml", GITHUB_HOSTS)).isEmpty();
    }

    private String readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("Resource %s should exist", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
