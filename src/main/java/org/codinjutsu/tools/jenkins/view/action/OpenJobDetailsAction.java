package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.jetbrains.annotations.NotNull;

public class OpenJobDetailsAction extends AnAction implements DumbAware {

    public OpenJobDetailsAction() {
        super("Show Job Details", "Show all builds for the selected job", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        ActionUtil.getProject(event).ifPresent(this::actionPerformed);
    }

    private void actionPerformed(@NotNull Project project) {
        final var job = BrowserPanel.getInstance(project).getSelectedJob();
        if (job == null) return;
        JobDetailPanel.getInstance(project).showJob(job);
        final var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(JobDetailPanel.TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.activate(null);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        final boolean isActualJob = ActionUtil.getBrowserPanel(event)
                .map(BrowserPanel::getSelectedJob)
                .map(job -> !job.getJobType().containNestedJobs())
                .orElse(false);
        event.getPresentation().setVisible(isActualJob);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
