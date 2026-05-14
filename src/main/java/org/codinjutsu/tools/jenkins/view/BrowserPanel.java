/*
 * Copyright (c) 2013 David Boissier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codinjutsu.tools.jenkins.view;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.CopyAction;
import com.intellij.lang.LangBundle;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.tree.TreeUtil;
import org.codinjutsu.tools.jenkins.*;
import org.codinjutsu.tools.jenkins.logic.*;
import org.codinjutsu.tools.jenkins.model.*;
import org.codinjutsu.tools.jenkins.util.CollectionUtil;
import org.codinjutsu.tools.jenkins.util.GuiUtil;
import org.codinjutsu.tools.jenkins.view.action.*;
import org.codinjutsu.tools.jenkins.view.action.settings.SortByStatusAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@State(name = "JenkinsBrowserPanel", storages = {
        @Storage(value = StoragePathMacros.PRODUCT_WORKSPACE_FILE, roamingType = RoamingType.DISABLED)
}, getStateRequiresEdt = true)
@Service(Service.Level.PROJECT)
public final class BrowserPanel extends SimpleToolWindowPanel implements PersistentStateComponent<JenkinsTreeState> {

    @NonNls
    public static final String POPUP_PLACE = "POPUP";
    @NonNls
    public static final String JENKINS_PANEL_PLACE = "jenkinsBrowserActions";
    private static final Logger logger = Logger.getInstance(BrowserPanel.class);
    private static final JobNameComparator JOB_NAME_COMPARATOR = new JobNameComparator();
    private static final Comparator<Job> sortByStatusComparator = Comparator.comparing(BrowserPanel::toBuildStatus);
    private static final Comparator<Job> sortByNameComparator = Comparator.comparing(Job::getNameToRenderSingleJob, JOB_NAME_COMPARATOR);
    @NotNull
    private final JenkinsTree jobTree;
    @NotNull
    private final Runnable refreshViewJob;
    @NotNull
    private final Project project;
    private final JenkinsAppSettings jenkinsAppSettings;
    @NotNull
    private final JenkinsSettings jenkinsSettings;
    private final Jenkins jenkins;
    private final RequestManagerInterface requestManager;
    private JPanel rootPanel;
    private JPanel jobPanel;
    private boolean sortedByBuildStatus;
    private ScheduledFuture<?> refreshViewFutureTask;
    @NotNull
    private TreeRoot treeRoot = new TreeRoot.JenkinsRoot(null);

    public BrowserPanel(@NotNull Project project) {
        super(true);
        this.project = project;

        final LoadSelectedViewJob loadSelectedViewJob = new LoadSelectedViewJob(project);
        this.refreshViewJob = loadSelectedViewJob::queue;

        requestManager = RequestManager.getInstance(project);
        jenkinsAppSettings = JenkinsAppSettings.getSafeInstance(project);
        jenkinsSettings = JenkinsSettings.getSafeInstance(project);
        setProvideQuickActions(false);

        jenkins = Jenkins.byDefault();
        jobTree = new JenkinsTree(project, jenkinsSettings, jenkins);
        updateDoubleClickAction(getDoubleClickAction(jenkinsAppSettings.getDoubleClickAction()));

        jobPanel.setLayout(new BorderLayout());
        jobPanel.add(ScrollPaneFactory.createScrollPane(jobTree.asComponent()), BorderLayout.CENTER);

        setContent(rootPanel);
    }

    @NotNull
    private static JobAction getDoubleClickAction(@NotNull DoubleClickAction doubleClickAction) {
        final JobAction action;
        switch (doubleClickAction) {
            case LOAD_BUILDS:
                action = JobActions.loadBuilds();
                break;
            case SHOW_LAST_LOG:
                action = JobActions.showLastLog();
                break;
            case TRIGGER_BUILD:
            default:
                action = JobActions.triggerBuild();
        }
        return action;
    }

    @NotNull
    private static BuildStatusEnum toBuildStatus(Job job) {
        return BuildStatusEnum.getStatusByColor(job.getColor());
    }

    public static BrowserPanel getInstance(Project project) {
        return project.getService(BrowserPanel.class);
    }

    private void updateDoubleClickAction(@NotNull JobAction doubleClickAction) {
        GuiUtil.runInSwingThread(() -> jobTree.updateDoubleClickAction(doubleClickAction));
    }

    /*whole method could be moved inside of ExecutorProvider (executor would expose interface that would allow to schedule
      new task previously cancelling previous ones) */
    public void initScheduledJobs() {
        final ExecutorService executorService = ExecutorService.getInstance(project);
        final ScheduledThreadPoolExecutor executor = executorService.getExecutor();
        executorService.safeTaskCancel(refreshViewFutureTask);
        executor.remove(refreshViewJob);

        if (jenkinsAppSettings.isServerUrlSet() && jenkinsAppSettings.getJobRefreshPeriod() > 0) {
            refreshViewFutureTask = executor.scheduleWithFixedDelay(refreshViewJob, jenkinsAppSettings.getJobRefreshPeriod(), jenkinsAppSettings.getJobRefreshPeriod(), TimeUnit.MINUTES);
        }
    }

    public Optional<Jenkins> getSelectedServer() {
        return jobTree.getLastSelectedPath(JenkinsTreeNode.RootNode.class)
                .map(JenkinsTreeNode.RootNode::jenkins);
    }

    public @NotNull Optional<Build> getSelectedBuild() {
        return jobTree.getLastSelectedPath(JenkinsTreeNode.BuildNode.class)
                .map(JenkinsTreeNode.BuildNode::build);
    }

    public @NotNull String getSelectedBuildUrl() {
        return getSelectedBuild().map(Build::getUrl).orElse("");
    }

    @Nullable
    public Job getSelectedJob() {
        return jobTree.getLastSelectedPath(JenkinsTreeNode.JobNode.class)
                .map(JenkinsTreeNode.JobNode::job).orElse(null);
    }

    public List<Job> getAllSelectedJobs() {
        return TreeUtil.collectSelectedObjectsOfType(jobTree.getTree(), JenkinsTreeNode.JobNode.class).stream()
                .map(JenkinsTreeNode.JobNode::job).collect(Collectors.toList());
    }

    @NotNull
    public List<Job> getAllJobs() {
        return CollectionUtil.flattenedJobs(jenkins.getJobs());
    }

    @NotNull
    public Optional<Job> getJob(String name) {
        return getAllJobs().stream().filter(job -> job.getNameToRenderSingleJob().equals(name)).findFirst();
    }

    public void setSortedByStatus(boolean sortedByBuildStatus) {
        this.sortedByBuildStatus = sortedByBuildStatus;
        jobTree.keepLastState(() -> jobTree.sortJobs(getCurrentSorting()));
    }

    @NotNull
    private Comparator<Job> getCurrentSorting() {
        return sortedByBuildStatus ? sortByStatusComparator : sortByNameComparator;
    }

    private void updateSelection() {
        jobTree.updateSelection();
    }

    public Jenkins getJenkins() {
        return jenkins;
    }

    public @Nullable View getCurrentSelectedView() {
        return treeRoot.getView();
    }

    @NotNull
    public RequestManagerInterface getJenkinsManager() {
        return requestManager;
    }

    public void loadJob(final Job job) {
        loadJob(job, j -> {});
    }

    public void loadJob(final Job job, Consumer<Job> loadedJob) {
        if (!SwingUtilities.isEventDispatchThread()) {
            logger.warn("BrowserPanel.loadJob called from outside of EDT");
        }
        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading job", true, new JenkinsBackgroundTask.JenkinsTask() {

                    private Job returnJob;

                    @Override
                    public void run(@NotNull RequestManagerInterface requestManager) {
                        returnJob = requestManager.loadJob(job);
                    }

                    @Override
                    public void onSuccess() {
                        JenkinsBackgroundTask.JenkinsTask.super.onSuccess();
                        job.updateContentWith(returnJob);
                        refreshJob(job);
                        loadedJob.accept(job);
                    }
                }).queue();
    }

    public void refreshJob(Job job) {
        updateJobNode(job);
    }

    private void updateJobNode(Job job) {
        GuiUtil.runInSwingThread(() -> jobTree.updateJobNode(job));
    }

    public void notifyInfoJenkinsToolWindow(@NotNull String message) {
        JenkinsNotifier.getInstance(project).notify(message, NotificationType.INFORMATION);
    }

    public void notifyInfoJenkinsToolWindow(@NotNull String message, String urlToOpen) {
        JenkinsNotifier.getInstance(project).notify(message, urlToOpen, NotificationType.INFORMATION);
    }

    public void notifyErrorJenkinsToolWindow(@NotNull String message) {
        JenkinsNotifier.getInstance(project).error(message);
    }

    public void handleEmptyConfiguration() {
        treeRoot = new TreeRoot.JenkinsRoot(null);
        setJobsUnavailable();
    }

    public void setJobsUnavailable() {
        clearView();
        jobTree.setJobsUnavailable();
    }

    private void clearView() {
        jobTree.clear();
    }

    public void postAuthenticationInitialization() {
        String lastSelectedViewName = jenkinsSettings.getLastSelectedView();
        View viewToLoad;
        if (StringUtil.isEmpty(lastSelectedViewName)) {
            viewToLoad = jenkins.getPrimaryView();
        } else {
            viewToLoad = jenkins.getViewByName(lastSelectedViewName);
        }
        loadView(viewToLoad);
    }

    public void initGui() {
        installActionsInToolbar();
        installActionsInPopupMenu();
    }

    private void installActionsInToolbar() {
        final ActionManager actionManager = ActionManager.getInstance();
        DefaultActionGroup actionGroup = new DefaultActionGroup("JenkinsToolbarGroup", false);
        actionGroup.add(new SelectViewAction(this));
        actionGroup.add(new RefreshNodeAction(this));
        actionGroup.add(new ResetFocusAction(this));
        actionGroup.add(actionManager.getAction(LoadBuildsAction.ACTION_ID));
        actionGroup.add(actionManager.getAction(RunBuildAction.ACTION_ID));
        actionGroup.add(actionManager.getAction(StopBuildAction.ACTION_ID));
        actionGroup.add(new SortByStatusAction(this));
        actionGroup.addSeparator();
        actionGroup.add(actionManager.getAction("Jenkins.ShowSettingsGroup"));

        GuiUtil.installActionGroupInToolBar(actionGroup, this, actionManager, JENKINS_PANEL_PLACE);
    }

    private void installActionsInPopupMenu() {
        DefaultActionGroup popupGroup = new DefaultActionGroup("JenkinsPopupAction", true);

        final CopyAction copyAction = new CopyAction();
        copyAction.getTemplatePresentation().setText(LangBundle.message("popup.title.copy"));
        copyAction.getTemplatePresentation().setIcon(AllIcons.Actions.Copy);
        popupGroup.add(copyAction);
        popupGroup.addSeparator();
        popupGroup.add(ActionManager.getInstance().getAction(RunBuildAction.ACTION_ID));
        popupGroup.add(ActionManager.getInstance().getAction(StopBuildAction.ACTION_ID));
        popupGroup.addSeparator();
        popupGroup.add(new ShowLogAction(BuildType.LAST));
        popupGroup.add(new ShowLogAction(BuildType.LAST_SUCCESSFUL));
        popupGroup.add(new ShowLogAction(BuildType.LAST_FAILED));
        popupGroup.add(new ShowBuildLogAction());
        popupGroup.addSeparator();
        popupGroup.add(new FocusOnItemAction(this));
        popupGroup.addSeparator();
        popupGroup.add(new GotoServerAction(this));
        popupGroup.add(new GotoJobPageAction(this));
        popupGroup.add(new GotoBuildPageAction(this));
        popupGroup.add(new GotoBuildConsolePageAction(this));
        popupGroup.add(new GotoBuildTestResultsPageAction(this));
        popupGroup.add(new GotoLastBuildPageAction(this));

        PopupHandler.installPopupMenu(jobTree.asComponent(), popupGroup, POPUP_PLACE);
    }

    public void loadView(final View view) {
        this.treeRoot = new TreeRoot.JenkinsRoot(view);
        if (!SwingUtilities.isEventDispatchThread()) {
            logger.warn("BrowserPanel.loadView called from outside EDT");
        }
        refreshViewJob.run();
    }

    public void refreshCurrentView() {
        if (!SwingUtilities.isEventDispatchThread()) {
            logger.warn("BrowserPanel.refreshCurrentView called outside EDT");
        }
        refreshViewJob.run();
    }

    public boolean isConfigured() {
        return jenkinsAppSettings.isServerUrlSet();
    }

    public void focusOnJob(@NotNull Job job) {
        this.treeRoot = new TreeRoot.JobRoot(job, treeRoot.getView());
        refreshViewJob.run();
    }

    public void clearFocusedJob() {
        this.treeRoot = new TreeRoot.JenkinsRoot(treeRoot.getView());
        refreshViewJob.run();
    }

    public boolean isFocused() {
        return !treeRoot.isAtGlobalRoot();
    }

    public void updateWorkspace(Jenkins jenkinsWorkspace) {
        jenkins.update(jenkinsWorkspace);
    }

    @Nullable
    @Override
    public JenkinsTreeState getState() {
        return jobTree.getState();
    }

    @Override
    public void loadState(@NotNull JenkinsTreeState state) {
        jobTree.loadState(state);
    }

    public void reloadConfiguration(@NotNull JenkinsAppSettings newJenkinsAppSettings) {
        updateDoubleClickAction(getDoubleClickAction(newJenkinsAppSettings.getDoubleClickAction()));
    }

    public void expandSelectedJob() {
        GuiUtil.runInSwingThread(() -> Optional.ofNullable(jobTree.getLastSelectedPathComponent())
                .filter(node -> node.getUserObject() instanceof JenkinsTreeNode.JobNode)
                .ifPresent(node -> jobTree.getTree().expandPath(new TreePath(node.getPath()))));
    }

    private class LoadSelectedViewJob implements JenkinsBackgroundTask.JenkinsTask {

        @NotNull
        private final JenkinsBackgroundTask task;

        public LoadSelectedViewJob(@NotNull Project project) {
            this.task = JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                    "Loading Jenkins Jobs", true, LoadSelectedViewJob.this);
        }

        @Override
        public void run(@NotNull RequestManagerInterface requestManager) {
            try {
                setTreeBusy(true);
                if (!resolveViewRoot()) return;
                loadJobs();
            } finally {
                setTreeBusy(false);
            }
        }

        private boolean resolveViewRoot() {
            if (treeRoot instanceof TreeRoot.JenkinsRoot jr && jr.view() == null) {
                View primaryView = jenkins.getPrimaryView();
                if (primaryView == null) return false;
                treeRoot = new TreeRoot.JenkinsRoot(primaryView);
            }
            return true;
        }

        @Override
        public void onSuccess() {
            GuiUtil.runInSwingThread(this::fillJobTree);
        }

        private void loadJobs() {
            if (SwingUtilities.isEventDispatchThread()) {
                logger.warn("BrowserPanel.loadJobs called from EDT");
            }
            final List<Job> jobs = treeRoot.loadJobs(requestManager);
            if (jenkinsAppSettings.isAutoLoadBuilds()) {
                for (Job job : jobs) {
                    job.setLastBuilds(requestManager.loadBuilds(job));
                }
            }
            if (treeRoot instanceof TreeRoot.JenkinsRoot jr && jr.view() != null) {
                jenkinsSettings.setLastSelectedView(jr.view().getName());
            }
            jenkins.setJobs(jobs);
        }

        private void fillJobTree() {
            final List<Job> jobList = jenkins.getJobs();
            jobTree.keepLastState(() -> {
                treeRoot.applyToTree(jobTree, jobList);
                jobTree.sortJobs(getCurrentSorting());
            });
        }

        private void setTreeBusy(final boolean isBusy) {
            GuiUtil.runInSwingThread(() -> jobTree.getTree().setPaintBusy(isBusy));
        }

        public void queue() {
            task.queue();
        }
    }

    private sealed interface TreeRoot {

        @NotNull List<Job> loadJobs(@NotNull RequestManagerInterface rm);

        void applyToTree(@NotNull JenkinsTree tree, @NotNull List<Job> jobs);

        @Nullable View getView();

        boolean isAtGlobalRoot();

        record JenkinsRoot(@Nullable View view) implements TreeRoot {
            @Override
            public @NotNull List<Job> loadJobs(@NotNull RequestManagerInterface rm) {
                return rm.loadJenkinsView(view);
            }

            @Override
            public void applyToTree(@NotNull JenkinsTree tree, @NotNull List<Job> jobs) {
                tree.setJobs(jobs);
            }

            @Override
            public @Nullable View getView() {
                return view;
            }

            @Override
            public boolean isAtGlobalRoot() {
                return true;
            }
        }

        record JobRoot(@NotNull Job job, @Nullable View parentView) implements TreeRoot {
            @Override
            public @NotNull List<Job> loadJobs(@NotNull RequestManagerInterface rm) {
                return rm.loadChildJobs(job);
            }

            @Override
            public void applyToTree(@NotNull JenkinsTree tree, @NotNull List<Job> jobs) {
                tree.setFocusedJob(job, jobs);
            }

            @Override
            public @Nullable View getView() {
                return parentView;
            }

            @Override
            public boolean isAtGlobalRoot() {
                return false;
            }
        }
    }
}
