package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.codinjutsu.tools.jenkins.model.Build;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.codinjutsu.tools.jenkins.view.action.pipeline.PipelinePanel;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class ShowPipelineAction extends AnAction implements DumbAware {

    public static final String ACTION_ID = "Jenkins.ShowPipelineStages";

    public ShowPipelineAction() {
        super("Show Pipeline Stages", "Show build pipeline stages and step logs",
                AllIcons.Vcs.Branch);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        final Project project = event.getProject();
        if (project == null) return;
        resolveSelectedBuild(event).ifPresent(build -> PipelinePanel.show(project, build));
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        final boolean hasBuild = resolveSelectedBuild(event).isPresent();
        event.getPresentation().setVisible(hasBuild);
        event.getPresentation().setEnabled(hasBuild);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @NotNull
    private static Optional<Build> resolveSelectedBuild(@NotNull AnActionEvent event) {
        // Try BrowserPanel first, then JobDetailPanel
        return ActionUtil.getBrowserPanel(event)
                .flatMap(BrowserPanel::getSelectedBuild)
                .or(() -> Optional.ofNullable(event.getProject())
                        .map(JobDetailPanel::getInstance)
                        .flatMap(JobDetailPanel::getSelectedBuild));
    }
}
