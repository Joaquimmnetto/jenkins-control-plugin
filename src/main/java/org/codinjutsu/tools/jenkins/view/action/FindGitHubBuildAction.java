package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.JenkinsControlIcons;
import org.codinjutsu.tools.jenkins.JenkinsControlBundle;
import org.codinjutsu.tools.jenkins.JenkinsSettings;
import org.codinjutsu.tools.jenkins.logic.JenkinsNotifier;
import org.codinjutsu.tools.jenkins.logic.github.GitHubClient;
import org.codinjutsu.tools.jenkins.logic.github.JenkinsJobMatcher;
import org.codinjutsu.tools.jenkins.logic.github.LocalGitContext;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class FindGitHubBuildAction extends AnAction implements DumbAware {

    public FindGitHubBuildAction() {
        super(JenkinsControlBundle.message("action.Jenkins.FindGitHubBuild.text"),
                JenkinsControlBundle.message("action.Jenkins.FindGitHubBuild.description"),
                JenkinsControlIcons.GITHUB);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        ActionUtil.getProject(event).ifPresent(this::actionPerformed);
    }

    private void actionPerformed(@NotNull Project project) {
        new Task.Backgroundable(project,
                JenkinsControlBundle.message("action.Jenkins.FindGitHubBuild.text"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                runAction(project);
            }
        }.queue();
    }

    static void runAction(@NotNull Project project) {
        final JenkinsSettings settings = JenkinsSettings.getSafeInstance(project);
        final Optional<LocalGitContext> contextOpt = LocalGitContext.read(project, settings.getGithubApiUrl());
        if (contextOpt.isEmpty()) {
            notifyWarning(project, JenkinsControlBundle.message("notification.github.noRepository"));
            return;
        }
        final LocalGitContext context = contextOpt.get();

        int prNumber = -1;
        try {
            final GitHubClient client = new GitHubClient(settings.getGithubApiUrl(),
                    settings.getGithubToken(), settings.getConnectionTimeout());
            prNumber = client.findOpenPullRequestNumber(context.owner(), context.repo(), context.branch())
                    .orElse(-1);
        } catch (GitHubClient.GitHubApiException e) {
            notifyWarning(project, JenkinsControlBundle.message("notification.github.lookupFailed",
                    e.getStatusCode() > 0 ? (e.getStatusCode() + " " + e.getMessage()) : e.getMessage()));
        }

        final Optional<Job> matched = new JenkinsJobMatcher(project).find(context, prNumber);
        if (matched.isEmpty()) {
            final String target = prNumber > 0 ? ("PR-" + prNumber) : context.branch();
            notifyWarning(project, JenkinsControlBundle.message("notification.github.noMatchingJob", target));
            return;
        }
        showJobOnEdt(project, matched.get());
    }

    private static void showJobOnEdt(@NotNull Project project, @NotNull Job job) {
        ApplicationManager.getApplication().invokeLater(() -> {
            JobDetailPanel.getInstance(project).showJob(job);
            final var toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow(JobDetailPanel.TOOL_WINDOW_ID);
            if (toolWindow != null) {
                toolWindow.activate(null);
            }
        });
    }

    private static void notifyWarning(@NotNull Project project, @NotNull String message) {
        JenkinsNotifier.getInstance(project).notify(message, NotificationType.WARNING);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        final boolean hasJobs = ActionUtil.getBrowserPanel(event)
                .map(BrowserPanel::getAllJobs)
                .map(jobs -> !jobs.isEmpty())
                .orElse(false);
        event.getPresentation().setEnabled(hasJobs);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
