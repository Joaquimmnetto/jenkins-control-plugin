package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.jetbrains.annotations.NotNull;

public class ShowJobBuildLogAction extends AnAction implements DumbAware {

    private final JobDetailPanel jobDetailPanel;

    public ShowJobBuildLogAction(@NotNull JobDetailPanel jobDetailPanel) {
        super("Show Log", "Show build log", AllIcons.Actions.Show);
        this.jobDetailPanel = jobDetailPanel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        jobDetailPanel.getSelectedBuild().ifPresent(build -> {
            final LogToolWindow logToolWindow = new LogToolWindow(jobDetailPanel.getProject());
            logToolWindow.showLog(build);
        });
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
