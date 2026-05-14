package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.jetbrains.annotations.NotNull;

public class GotoJobBuildPageAction extends AnAction implements DumbAware {

    private final JobDetailPanel jobDetailPanel;

    public GotoJobBuildPageAction(@NotNull JobDetailPanel jobDetailPanel) {
        super("Go to the build page", "Open the build page in a web browser", null);
        this.jobDetailPanel = jobDetailPanel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        BrowserUtil.browse(jobDetailPanel.getSelectedBuildUrl());
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setVisible(jobDetailPanel.getSelectedBuild().isPresent());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
