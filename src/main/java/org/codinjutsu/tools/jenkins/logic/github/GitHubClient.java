package org.codinjutsu.tools.jenkins.logic.github;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.intellij.openapi.diagnostic.Logger;
import org.codinjutsu.tools.jenkins.exception.JenkinsPluginRuntimeException;
import org.codinjutsu.tools.jenkins.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class GitHubClient {

    private static final Logger LOG = Logger.getInstance(GitHubClient.class);

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String token;
    private final Duration requestTimeout;

    public GitHubClient(@NotNull String apiBaseUrl, @NotNull String token, int timeoutSeconds) {
        this(HttpClient.newHttpClient(), apiBaseUrl, token, timeoutSeconds);
    }

    GitHubClient(@NotNull HttpClient httpClient, @NotNull String apiBaseUrl, @NotNull String token,
                 int timeoutSeconds) {
        this.httpClient = httpClient;
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.token = token;
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    public @NotNull Optional<Integer> findOpenPullRequestNumber(@NotNull String owner, @NotNull String repo,
                                                                @NotNull String branch) {
        final String url = apiBaseUrl + "/repos/" + encode(owner) + "/" + encode(repo)
                + "/pulls?head=" + encode(owner) + ":" + encode(branch)
                + "&state=open&per_page=1";
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "jenkins-control-plugin")
                .GET();
        if (StringUtil.isNotBlank(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        try {
            final HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            final int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return parseFirstPrNumber(response.body());
            }
            LOG.warn("GitHub API call failed: status=" + status + " body=" + response.body());
            throw new GitHubApiException(status, extractErrorMessage(response.body()));
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("GitHub API call threw: " + e.getMessage(), e);
            throw new GitHubApiException(-1, e.getMessage());
        }
    }

    static @NotNull Optional<Integer> parseFirstPrNumber(@NotNull String body) {
        try {
            final Object parsed = Jsoner.deserialize(body);
            if (!(parsed instanceof JsonArray array) || array.isEmpty()) {
                return Optional.empty();
            }
            final Object first = array.get(0);
            if (!(first instanceof JsonObject pr)) {
                return Optional.empty();
            }
            final Object number = pr.get("number");
            if (number instanceof Number numericNumber) {
                return Optional.of(numericNumber.intValue());
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.warn("Failed to parse GitHub PR response: " + e.getMessage(), e);
            throw new JenkinsPluginRuntimeException("Invalid GitHub response: " + e.getMessage());
        }
    }

    static @NotNull String extractErrorMessage(@NotNull String body) {
        try {
            final Object parsed = Jsoner.deserialize(body);
            if (parsed instanceof JsonObject obj && obj.get("message") instanceof String msg) {
                return msg;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return body;
    }

    private static @NotNull String encode(@NotNull String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static @NotNull String stripTrailingSlash(@NotNull String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static class GitHubApiException extends RuntimeException {
        private final int statusCode;

        public GitHubApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
