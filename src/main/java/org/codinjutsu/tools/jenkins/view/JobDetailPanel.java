package org.codinjutsu.tools.jenkins.view;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.SimpleTree;
import org.codinjutsu.tools.jenkins.view.BuildStatusEnumRenderer;
import org.codinjutsu.tools.jenkins.logic.JenkinsBackgroundTask;
import org.codinjutsu.tools.jenkins.logic.JenkinsBackgroundTaskFactory;
import org.codinjutsu.tools.jenkins.logic.RequestManagerInterface;
import org.codinjutsu.tools.jenkins.model.Build;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.util.GuiUtil;
import org.codinjutsu.tools.jenkins.view.action.FindGitHubBuildAction;
import org.codinjutsu.tools.jenkins.view.action.GotoJobBuildPageAction;
import org.codinjutsu.tools.jenkins.view.action.ShowJobBuildLogAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.List;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class JobDetailPanel extends SimpleToolWindowPanel {

    public static final String TOOL_WINDOW_ID = "Jenkins Job";
    private static final String POPUP_PLACE = "JENKINS_JOB_POPUP";
    private static final String TOOLBAR_PLACE = "JENKINS_JOB_TOOLBAR";

    private final Project project;
    private final SimpleTree buildTree;
    private final DefaultTreeModel treeModel;
    @Nullable
    private Job currentJob;

    public JobDetailPanel(@NotNull Project project) {
        super(true);
        this.project = project;

        final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(root);
        buildTree = new SimpleTree();
        buildTree.setModel(treeModel);
        buildTree.setCellRenderer(new JenkinsTreeRenderer(BuildStatusEnumRenderer.getInstance(project)));
        buildTree.setRootVisible(true);
        buildTree.setShowsRootHandles(true);
        buildTree.getEmptyText().setText("Select a job in the Jenkins panel to see its builds");

        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(ScrollPaneFactory.createScrollPane(buildTree), BorderLayout.CENTER);
        setContent(panel);
    }

    public static JobDetailPanel getInstance(@NotNull Project project) {
        return project.getService(JobDetailPanel.class);
    }

    public @NotNull Project getProject() {
        return project;
    }

    public void initGui() {
        installActionsInPopupMenu();
        installToolbar();
    }

    private void installActionsInPopupMenu() {
        final DefaultActionGroup popupGroup = new DefaultActionGroup("JenkinsJobPopupAction", true);
        popupGroup.add(new GotoJobBuildPageAction(this));
        popupGroup.add(new ShowJobBuildLogAction(this));
        PopupHandler.installPopupMenu(buildTree, popupGroup, POPUP_PLACE);
    }

    private void installToolbar() {
        final DefaultActionGroup toolbarGroup = new DefaultActionGroup("JenkinsJobToolbarActions", false);
        toolbarGroup.add(new FindGitHubBuildAction());
        final ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar(TOOLBAR_PLACE, toolbarGroup, true);
        toolbar.setTargetComponent(buildTree);
        setToolbar(toolbar.getComponent());
    }

    public void showJob(@NotNull Job job) {
        this.currentJob = job;
        GuiUtil.runInSwingThread(() -> populateTree(job));
        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading builds for " + job.getNameToRenderSingleJob(), true,
                new JenkinsBackgroundTask.JenkinsTask() {
                    private List<Build> builds;

                    @Override
                    public void run(@NotNull RequestManagerInterface requestManager) {
                        builds = requestManager.loadBuilds(job);
                    }

                    @Override
                    public void onSuccess() {
                        job.setLastBuilds(builds);
                        if (job == currentJob) {
                            GuiUtil.runInSwingThread(() -> populateTree(job));
                        }
                    }
                }).queue();
    }

    private void populateTree(@NotNull Job job) {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode(new JenkinsTreeNode.JobNode(job));
        job.getLastBuilds().stream()
                .map(build -> new DefaultMutableTreeNode(new JenkinsTreeNode.BuildNode(build)))
                .forEach(root::add);
        treeModel.setRoot(root);
        treeModel.reload();
        buildTree.expandRow(0);
    }

    public @NotNull Optional<Build> getSelectedBuild() {
        return Optional.ofNullable(buildTree.getLastSelectedPathComponent())
                .filter(DefaultMutableTreeNode.class::isInstance)
                .map(DefaultMutableTreeNode.class::cast)
                .map(DefaultMutableTreeNode::getUserObject)
                .filter(JenkinsTreeNode.BuildNode.class::isInstance)
                .map(JenkinsTreeNode.BuildNode.class::cast)
                .map(JenkinsTreeNode.BuildNode::build);
    }

    public @NotNull String getSelectedBuildUrl() {
        return getSelectedBuild().map(Build::getUrl).orElse("");
    }
}
