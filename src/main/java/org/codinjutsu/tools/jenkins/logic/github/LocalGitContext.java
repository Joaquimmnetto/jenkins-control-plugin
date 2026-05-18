package org.codinjutsu.tools.jenkins.logic.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.codinjutsu.tools.jenkins.util.StringUtil;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LocalGitContext(
        @NotNull String branch,
        @NotNull String owner,
        @NotNull String repo,
        @NotNull String remoteUrl) {

    private static final Logger LOG = Logger.getInstance(LocalGitContext.class);

    private static final List<Pattern> GITHUB_URL_PATTERNS = List.of(
            Pattern.compile("^git@([^:]+):([^/]+)/([^/]+?)(?:\\.git)?/?$"),
            Pattern.compile("^ssh://(?:[^@]+@)?([^/]+)[:/]+([^/]+)/([^/]+?)(?:\\.git)?/?$"),
            Pattern.compile("^https?://(?:[^@]+@)?([^/]+)/([^/]+)/([^/]+?)(?:\\.git)?/?$")
    );

    private static final Set<String> DEFAULT_GITHUB_HOSTS = Set.of("github.com");

    public static @NotNull Optional<LocalGitContext> read(@NotNull Project project, @NotNull String githubApiUrl) {
        final String basePath = project.getBasePath();
        if (StringUtil.isBlank(basePath)) {
            return Optional.empty();
        }
        try (Repository repository = new FileRepositoryBuilder()
                .findGitDir(new File(basePath))
                .readEnvironment()
                .build()) {
            if (repository.getDirectory() == null) {
                return Optional.empty();
            }
            final String branch = repository.getBranch();
            if (StringUtil.isBlank(branch) || isDetachedHeadSha(branch)) {
                return Optional.empty();
            }
            return findGithubRemote(repository.getConfig(), githubApiUrl)
                    .flatMap(remote -> parseOwnerRepo(remote.url, hostsFor(githubApiUrl))
                            .map(parsed -> new LocalGitContext(branch, parsed.owner, parsed.repo, remote.url)));
        } catch (IOException e) {
            LOG.warn("Unable to read local git repository at " + basePath, e);
            return Optional.empty();
        }
    }

    private static boolean isDetachedHeadSha(@NotNull String branch) {
        return branch.length() == 40 && branch.chars().allMatch(c -> "0123456789abcdef".indexOf(c) >= 0);
    }

    private static @NotNull Optional<RemoteUrl> findGithubRemote(@NotNull Config config, @NotNull String githubApiUrl) {
        final Set<String> hosts = hostsFor(githubApiUrl);
        final Set<String> remoteNames = config.getSubsections("remote");
        RemoteUrl firstMatch = null;
        for (String name : remoteNames) {
            final String url = config.getString("remote", name, "url");
            if (StringUtil.isBlank(url)) continue;
            if (!isGithubUrl(url, hosts)) continue;
            if ("origin".equals(name)) {
                return Optional.of(new RemoteUrl(name, url));
            }
            if (firstMatch == null) {
                firstMatch = new RemoteUrl(name, url);
            }
        }
        return Optional.ofNullable(firstMatch);
    }

    static @NotNull Set<String> hostsFor(@NotNull String githubApiUrl) {
        if (StringUtil.isBlank(githubApiUrl)) return DEFAULT_GITHUB_HOSTS;
        try {
            final java.net.URI uri = java.net.URI.create(githubApiUrl);
            final String host = uri.getHost();
            if (StringUtil.isBlank(host)) return DEFAULT_GITHUB_HOSTS;
            if ("api.github.com".equals(host)) return DEFAULT_GITHUB_HOSTS;
            // Enterprise: api host typically has an "api." prefix or a "/api/v3" path.
            final String enterpriseHost = host.startsWith("api.") ? host.substring(4) : host;
            return Set.of(enterpriseHost);
        } catch (IllegalArgumentException e) {
            return DEFAULT_GITHUB_HOSTS;
        }
    }

    static boolean isGithubUrl(@NotNull String url, @NotNull Set<String> hosts) {
        for (Pattern p : GITHUB_URL_PATTERNS) {
            final Matcher m = p.matcher(url);
            if (m.matches() && hosts.contains(m.group(1).toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static @NotNull Optional<OwnerRepo> parseOwnerRepo(@NotNull String url, @NotNull Set<String> hosts) {
        for (Pattern p : GITHUB_URL_PATTERNS) {
            final Matcher m = p.matcher(url);
            if (m.matches() && hosts.contains(m.group(1).toLowerCase())) {
                return Optional.of(new OwnerRepo(m.group(2), m.group(3)));
            }
        }
        return Optional.empty();
    }

    record OwnerRepo(@NotNull String owner, @NotNull String repo) {
    }

    private record RemoteUrl(@NotNull String name, @NotNull String url) {
    }

    @Nullable
    public String pullRequestTargetName(int prNumber) {
        return prNumber > 0 ? "PR-" + prNumber : null;
    }
}
