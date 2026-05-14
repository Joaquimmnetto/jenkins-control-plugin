package org.codinjutsu.tools.jenkins;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.jetbrains.annotations.NotNull;

public class JenkinsJobToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        final JobDetailPanel jobDetailPanel = JobDetailPanel.getInstance(project);
        jobDetailPanel.initGui();

        final Content content = ContentFactory.getInstance().createContent(jobDetailPanel, null, false);
        toolWindow.setType(ToolWindowType.DOCKED, null);
        final ContentManager contentManager = toolWindow.getContentManager();
        contentManager.addContent(content);
    }
}
