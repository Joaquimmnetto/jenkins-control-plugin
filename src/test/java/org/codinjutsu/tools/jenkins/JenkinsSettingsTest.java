package org.codinjutsu.tools.jenkins;

import com.intellij.credentialStore.CredentialStoreManager;
import com.intellij.credentialStore.PasswordSafeSettings;
import com.intellij.credentialStore.ProviderType;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.passwordSafe.impl.TestPasswordSafeImpl;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.model.JobType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JenkinsSettingsTest {

    private static final @NotNull Disposable DO_NOTHING = () -> {
    };

    private JenkinsSettings settings;
    private final Map<String, String> env = new HashMap<>();

    @BeforeEach
    void setUp() {
        final var application = MockApplication.setUp(DO_NOTHING);
        application.registerService(PasswordSafeSettings.class);
        application.registerService(CredentialStoreManager.class, new MemoryOnlyCredentials());
        application.registerService(PasswordSafe.class, new TestPasswordSafeImpl());

        settings = new JenkinsSettings();
        settings.setEnvLookup(env::get);
    }

    @AfterEach
    void tearDown() {
        ApplicationManager.setApplication(null);
    }

    @Test
    void githubTokenFromPasswordSafeWinsOverEnvVars() {
        env.put("GH_TOKEN", "from-env");
        settings.setGithubToken("from-safe");

        assertThat(settings.getGithubToken()).isEqualTo("from-safe");
    }

    @Test
    void githubTokenFallsBackToGhTokenEnvVar() {
        env.put("GH_TOKEN", "gh-env");

        assertThat(settings.getGithubToken()).isEqualTo("gh-env");
    }

    @Test
    void githubTokenFallsBackToGithubTokenEnvVar() {
        env.put("GITHUB_TOKEN", "github-env");

        assertThat(settings.getGithubToken()).isEqualTo("github-env");
    }

    @Test
    void githubTokenReturnsEmptyWhenNothingIsSet() {
        assertThat(settings.getGithubToken()).isEmpty();
    }

    @Test
    void githubApiUrlDefaultsToApiGithubCom() {
        assertThat(settings.getGithubApiUrl()).isEqualTo("https://api.github.com");
    }

    @Test
    void githubApiUrlRoundTrips() {
        settings.setGithubApiUrl("https://github.example.com/api/v3");
        assertThat(settings.getGithubApiUrl()).isEqualTo("https://github.example.com/api/v3");
    }

    @Test
    void blankApiUrlResetsToDefault() {
        settings.setGithubApiUrl("");
        assertThat(settings.getGithubApiUrl()).isEqualTo("https://api.github.com");
    }

    @Test
    void hasFocusedJobReturnsFalseInitially() {
        assertThat(settings.hasFocusedJob()).isFalse();
    }

    @Test
    void hasFocusedJobReturnsTrueAfterSet() {
        settings.setFocusedJob("http://jenkins/job/my-folder/", "my-folder", "my-folder", "FOLDER");
        assertThat(settings.hasFocusedJob()).isTrue();
    }

    @Test
    void clearFocusedJobResetsHasFocusedJob() {
        settings.setFocusedJob("http://jenkins/job/my-folder/", "my-folder", "my-folder", "FOLDER");
        settings.clearFocusedJob();
        assertThat(settings.hasFocusedJob()).isFalse();
    }

    @Test
    void restoreFocusedJobReturnsJobWithCorrectFields() {
        settings.setFocusedJob(
                "http://jenkins/job/my-folder/", "my-folder", "team/my-folder", "FOLDER");

        Job job = settings.restoreFocusedJob();

        assertThat(job.getUrl()).isEqualTo("http://jenkins/job/my-folder/");
        assertThat(job.getName()).isEqualTo("my-folder");
        assertThat(job.getFullName()).isEqualTo("team/my-folder");
        assertThat(job.getJobType()).isEqualTo(JobType.FOLDER);
    }

    @Test
    void restoreFocusedJobPreservesMultiBranchType() {
        settings.setFocusedJob(
                "http://jenkins/job/pipeline/", "pipeline", "pipeline", "MULTI_BRANCH");

        assertThat(settings.restoreFocusedJob().getJobType()).isEqualTo(JobType.MULTI_BRANCH);
    }

    @Test
    void restoreFocusedJobDefaultsToFolderForUnknownType() {
        settings.setFocusedJob(
                "http://jenkins/job/pipeline/", "pipeline", "pipeline", "UNKNOWN_FUTURE_TYPE");

        assertThat(settings.restoreFocusedJob().getJobType()).isEqualTo(JobType.FOLDER);
    }

    private static class MemoryOnlyCredentials implements CredentialStoreManager {
        @Override
        public boolean isSupported(@NotNull ProviderType providerType) {
            return providerType == ProviderType.MEMORY_ONLY;
        }

        @NotNull
        @Override
        public ProviderType defaultProvider() {
            return ProviderType.MEMORY_ONLY;
        }

        @NotNull
        @Override
        public List<ProviderType> availableProviders() {
            return List.of(ProviderType.MEMORY_ONLY);
        }
    }
}
