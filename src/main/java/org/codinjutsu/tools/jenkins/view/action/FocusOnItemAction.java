package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FocusOnItemAction extends AnAction implements DumbAware {

    private final BrowserPanel browserPanel;

    public FocusOnItemAction(BrowserPanel browserPanel) {
        super("Focus on This Item", "Show only this folder's contents in the tree", null);
        this.browserPanel = browserPanel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        final Job selectedJob = browserPanel.getSelectedJob();
        if (selectedJob != null) {
            browserPanel.focusOnJob(selectedJob);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        final Job selectedJob = browserPanel.getSelectedJob();
        final boolean isFocusable = selectedJob != null && selectedJob.getJobType().containNestedJobs();
        event.getPresentation().setVisible(isFocusable);
        event.getPresentation().setEnabled(isFocusable);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
