package org.codinjutsu.tools.jenkins.logic.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.codinjutsu.tools.jenkins.logic.RequestManager;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.model.JobType;
import org.codinjutsu.tools.jenkins.util.CollectionUtil;
import org.codinjutsu.tools.jenkins.util.StringUtil;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class JenkinsJobMatcher {

    private static final Logger LOG = Logger.getInstance(JenkinsJobMatcher.class);

    private final Project project;
    private final Function<String, String> configXmlLoader;
    private final Map<String, LocalGitContext.OwnerRepo> ownerRepoCache = new LinkedHashMap<>();

    public JenkinsJobMatcher(@NotNull Project project) {
        this(project, jobUrl -> RequestManager.getInstance(project).loadJobConfigXml(jobUrl));
    }

    JenkinsJobMatcher(@NotNull Project project, @NotNull Function<String, String> configXmlLoader) {
        this.project = project;
        this.configXmlLoader = configXmlLoader;
    }

    public @NotNull Optional<Job> find(@NotNull LocalGitContext context, int pullRequestNumber) {
        final List<Job> allJobs = BrowserPanel.getInstance(project).getAllJobs();
        final String prTarget = pullRequestNumber > 0 ? "PR-" + pullRequestNumber : null;
        final String branchTarget = context.branch();
        final Set<String> ghHosts = LocalGitContext.hostsFor(loadGithubApiUrl());

        for (Job job : allJobs) {
            if (job.getJobType() != JobType.MULTI_BRANCH) continue;
            final Optional<LocalGitContext.OwnerRepo> jobRepo = resolveOwnerRepo(job, ghHosts);
            if (jobRepo.isEmpty()) continue;
            if (!matches(jobRepo.get(), context)) continue;
            final Optional<Job> picked = pickChild(job, prTarget, branchTarget);
            if (picked.isPresent()) return picked;
        }
        return findByNameFlat(allJobs, prTarget, branchTarget);
    }

    private @NotNull String loadGithubApiUrl() {
        return org.codinjutsu.tools.jenkins.JenkinsSettings.getSafeInstance(project).getGithubApiUrl();
    }

    private @NotNull Optional<LocalGitContext.OwnerRepo> resolveOwnerRepo(@NotNull Job job,
                                                                         @NotNull Set<String> ghHosts) {
        final String cacheKey = job.getUrl();
        if (ownerRepoCache.containsKey(cacheKey)) {
            return Optional.ofNullable(ownerRepoCache.get(cacheKey));
        }
        Optional<LocalGitContext.OwnerRepo> result;
        try {
            final String xml = configXmlLoader.apply(job.getUrl());
            result = extractOwnerRepoFromConfigXml(xml, ghHosts);
        } catch (Exception e) {
            LOG.warn("Failed to load config.xml for " + job.getUrl() + ": " + e.getMessage());
            result = Optional.empty();
        }
        ownerRepoCache.put(cacheKey, result.orElse(null));
        return result;
    }

    private static boolean matches(@NotNull LocalGitContext.OwnerRepo a, @NotNull LocalGitContext context) {
        return a.owner().equalsIgnoreCase(context.owner()) && a.repo().equalsIgnoreCase(context.repo());
    }

    private static @NotNull Optional<Job> pickChild(@NotNull Job parent, String prTarget, String branchTarget) {
        if (StringUtil.isNotBlank(prTarget)) {
            final Optional<Job> pr = parent.getNestedJobs().stream()
                    .filter(j -> prTarget.equals(j.getName()))
                    .findFirst();
            if (pr.isPresent()) return pr;
        }
        return parent.getNestedJobs().stream()
                .filter(j -> branchTarget.equals(j.getName()))
                .findFirst();
    }

    private static @NotNull Optional<Job> findByNameFlat(@NotNull List<Job> allJobs, String prTarget,
                                                         String branchTarget) {
        final List<Job> flattened = CollectionUtil.flattenedJobs(allJobs);
        if (StringUtil.isNotBlank(prTarget)) {
            final Optional<Job> pr = flattened.stream()
                    .filter(j -> prTarget.equals(j.getName()))
                    .findFirst();
            if (pr.isPresent()) return pr;
        }
        return flattened.stream()
                .filter(j -> branchTarget.equals(j.getName()))
                .findFirst();
    }

    static @NotNull Optional<LocalGitContext.OwnerRepo> extractOwnerRepoFromConfigXml(@NotNull String xml,
                                                                                     @NotNull Set<String> ghHosts) {
        if (StringUtil.isBlank(xml)) return Optional.empty();
        final Document doc;
        try {
            doc = newSafeBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            LOG.debug("Could not parse config.xml: " + e.getMessage());
            return Optional.empty();
        }
        return firstFromGitHubSCMSource(doc)
                .or(() -> firstFromAnyUrlText(doc, ghHosts));
    }

    private static @NotNull Optional<LocalGitContext.OwnerRepo> firstFromGitHubSCMSource(@NotNull Document doc) {
        final NodeList owners = doc.getElementsByTagName("repoOwner");
        final NodeList repos = doc.getElementsByTagName("repository");
        if (owners.getLength() == 0 || repos.getLength() == 0) return Optional.empty();
        final String owner = textOf(owners.item(0));
        final String repo = textOf(repos.item(0));
        if (StringUtil.isBlank(owner) || StringUtil.isBlank(repo)) return Optional.empty();
        return Optional.of(new LocalGitContext.OwnerRepo(owner.trim(), repo.trim()));
    }

    private static @NotNull Optional<LocalGitContext.OwnerRepo> firstFromAnyUrlText(@NotNull Document doc,
                                                                                   @NotNull Set<String> ghHosts) {
        for (String tag : List.of("url", "remote")) {
            final NodeList nodes = doc.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                final String candidate = textOf(nodes.item(i));
                if (StringUtil.isBlank(candidate)) continue;
                final Optional<LocalGitContext.OwnerRepo> parsed =
                        LocalGitContext.parseOwnerRepo(candidate.trim(), ghHosts);
                if (parsed.isPresent()) return parsed;
            }
        }
        return Optional.empty();
    }

    private static @NotNull String textOf(@NotNull Node node) {
        return Objects.requireNonNullElse(node.getTextContent(), "");
    }

    private static @NotNull DocumentBuilder newSafeBuilder() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }
}
