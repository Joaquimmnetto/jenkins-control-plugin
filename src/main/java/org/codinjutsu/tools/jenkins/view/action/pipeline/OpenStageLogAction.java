package org.codinjutsu.tools.jenkins.view.action.pipeline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.codinjutsu.tools.jenkins.logic.JenkinsBackgroundTaskFactory;
import org.codinjutsu.tools.jenkins.model.Build;
import org.codinjutsu.tools.jenkins.model.PipelineStage;
import org.codinjutsu.tools.jenkins.model.PipelineStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

class OpenStageLogAction extends AnAction implements DumbAware {

    private static final int MAX_HASMORE_ITERATIONS = 100;

    private final PipelinePanel panel;

    OpenStageLogAction(@NotNull PipelinePanel panel) {
        super("Open Stage Log", "Open full log for selected stage in editor",
                AllIcons.Actions.OpenNewTab);
        this.panel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) return;
        final PipelineStage stage = panel.getSelectedStage();
        final Build build = panel.getBuild();
        if (stage == null) return;

        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading stage log: " + stage.getName(),
                true,
                requestManager -> {
                    try {
                        final Path tmp = Files.createTempFile(
                                "jenkins-stage-" + sanitize(stage.getName()) + "-", ".log");
                        panel.registerTempFile(tmp);

                        try (final OutputStream out = Files.newOutputStream(tmp,
                                StandardOpenOption.APPEND)) {
                            final java.util.List<PipelineStep> steps;
                            if (stage.getSteps().isEmpty()) {
                                steps = requestManager.loadPipelineSteps(build, stage.getId());
                            } else {
                                steps = stage.getSteps();
                            }
                            for (final PipelineStep step : steps) {
                                ProgressManager.checkCanceled();
                                int iterations = 0;
                                boolean hasMore = true;
                                while (hasMore && iterations < MAX_HASMORE_ITERATIONS) {
                                    ProgressManager.checkCanceled();
                                    hasMore = requestManager.fetchNodeLog(build, step.getId(), out);
                                    iterations++;
                                }
                            }
                        }

                        final Path finalTmp = tmp;
                        javax.swing.SwingUtilities.invokeLater(() -> openFile(project, finalTmp));
                    } catch (IOException ex) {
                        throw new org.codinjutsu.tools.jenkins.exception.JenkinsPluginRuntimeException(
                                "Cannot write stage log: " + ex.getMessage());
                    }
                }
        ).queue();
    }

    private static void openFile(@NotNull Project project, @NotNull Path path) {
        final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile());
        if (vf != null) {
            FileEditorManager.getInstance(project).openFile(vf, true);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(panel.getSelectedStage() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @NotNull
    private static String sanitize(@NotNull String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
