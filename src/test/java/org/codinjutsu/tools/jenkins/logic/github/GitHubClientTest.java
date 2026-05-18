package org.codinjutsu.tools.jenkins.logic.github;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<HttpExchange> lastExchange = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "[]";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastExchange.set(exchange);
            final byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void returnsPullRequestNumber() {
        responseBody = "[{\"number\":42,\"title\":\"x\"}]";
        final GitHubClient client = new GitHubClient(HttpClient.newHttpClient(), baseUrl, "", 5);

        final Optional<Integer> pr = client.findOpenPullRequestNumber("acme", "widget", "feature");

        assertThat(pr).contains(42);
    }

    @Test
    void emptyResponseReturnsEmpty() {
        responseBody = "[]";
        final GitHubClient client = new GitHubClient(HttpClient.newHttpClient(), baseUrl, "", 5);

        assertThat(client.findOpenPullRequestNumber("acme", "widget", "feature")).isEmpty();
    }

    @Test
    void sendsBearerHeaderWhenTokenPresent() {
        responseBody = "[]";
        final GitHubClient client = new GitHubClient(HttpClient.newHttpClient(), baseUrl, "secret", 5);

        client.findOpenPullRequestNumber("acme", "widget", "feature");

        assertThat(lastExchange.get().getRequestHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer secret");
    }

    @Test
    void omitsBearerHeaderWhenTokenBlank() {
        responseBody = "[]";
        final GitHubClient client = new GitHubClient(HttpClient.newHttpClient(), baseUrl, "", 5);

        client.findOpenPullRequestNumber("acme", "widget", "feature");

        assertThat(lastExchange.get().getRequestHeaders().getFirst("Authorization")).isNull();
    }

    @Test
    void urlEncodesBranchWithSlashes() {
        responseBody = "[]";
        final GitHubClient client = new GitHubClient(HttpClient.newHttpClient(), baseUrl, "", 5);

        client.findOpenPullRequestNumber("acme", "widget", "feature/foo bar");

        final String query = lastExchange.get().getRequestURI().getRawQuery();
        // The JDK's URI may keep ':' literal; the important parts are encoding of '/' and space.
        assertThat(query).contains("feature%2Ffoo+bar");
        assertThat(query).contains("acme");
    }

    @Test
    void httpErrorThrowsApiException() {
        responseStatus = 401;
        responseBody = "{\"message\":\"Bad credentials\"}";
        final GitHubClient client = new GitHubClient(HttpClient.newHttpClient(), baseUrl, "", 5);

        Assertions.assertThatThrownBy(() -> client.findOpenPullRequestNumber("acme", "widget", "feature"))
                .isInstanceOf(GitHubClient.GitHubApiException.class)
                .hasMessageContaining("Bad credentials");
    }

    @Test
    void parseFirstPrNumberHandlesNoNumberField() {
        assertThat(GitHubClient.parseFirstPrNumber("[{\"title\":\"x\"}]")).isEmpty();
    }
}
