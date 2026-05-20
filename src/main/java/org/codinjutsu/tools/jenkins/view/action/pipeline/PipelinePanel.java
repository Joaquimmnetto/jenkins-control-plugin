package org.codinjutsu.tools.jenkins.view.action.pipeline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.JBUI;
import org.codinjutsu.tools.jenkins.logic.JenkinsBackgroundTaskFactory;
import org.codinjutsu.tools.jenkins.model.Build;
import org.codinjutsu.tools.jenkins.model.PipelineStage;
import org.codinjutsu.tools.jenkins.model.PipelineStep;
import org.codinjutsu.tools.jenkins.util.GuiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PipelinePanel implements Disposable {

    private static final String CARD_LOADING = "loading";
    private static final String CARD_UNAVAILABLE = "unavailable";
    private static final String CARD_CONTENT = "content";

    private final Project project;
    private final Build build;

    private final JPanel root;
    private final CardLayout cardLayout;
    private final JTree stageTree;
    private final DefaultMutableTreeNode stageTreeRoot;
    private final DefaultTreeModel stageTreeModel;
    private final JPanel stepsContainer;
    private final JLabel stepsHeader;

    // nodeId → temp log file
    private final ConcurrentHashMap<String, Path> logCache = new ConcurrentHashMap<>();
    // all temp files registered (including stage logs)
    private final List<Path> allTempFiles = new ArrayList<>();

    @Nullable
    private PipelineStage selectedStage;
    private List<PipelineStage> stages = List.of();

    public PipelinePanel(@NotNull Project project, @NotNull Build build) {
        this.project = project;
        this.build = build;

        stageTreeRoot = new DefaultMutableTreeNode("root");
        stageTreeModel = new DefaultTreeModel(stageTreeRoot);
        final SimpleTree simpleTree = new SimpleTree(stageTreeModel);
        simpleTree.setRootVisible(false);
        simpleTree.setShowsRootHandles(false);
        simpleTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        simpleTree.setCellRenderer(new PipelineStageTreeRenderer());
        simpleTree.getEmptyText().setText("Loading pipeline stages…");
        simpleTree.setBorder(JBUI.Borders.empty(4));
        stageTree = simpleTree;

        stepsContainer = new JPanel();
        stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));

        stepsHeader = new JLabel("Select a stage to view its steps");
        stepsHeader.setBorder(JBUI.Borders.empty(6, 8));
        stepsHeader.setForeground(JBUI.CurrentTheme.Label.disabledForeground());

        cardLayout = new CardLayout();
        root = new JPanel(cardLayout);

        root.add(buildLoadingCard(), CARD_LOADING);
        root.add(buildUnavailableCard(), CARD_UNAVAILABLE);
        root.add(buildContentCard(), CARD_CONTENT);

        cardLayout.show(root, CARD_LOADING);

        stageTree.addTreeSelectionListener(e -> {
            final var path = e.getPath();
            if (path == null) return;
            final Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (node instanceof PipelineStage stage) {
                selectedStage = stage;
                loadStepsForStage(stage);
            }
        });
    }

    @NotNull
    public JComponent getComponent() {
        return root;
    }

    @NotNull
    public Build getBuild() {
        return build;
    }

    @Nullable
    public PipelineStage getSelectedStage() {
        return selectedStage;
    }

    public void registerTempFile(@NotNull Path path) {
        allTempFiles.add(path);
    }

    public void loadStages() {
        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading pipeline stages for " + build.getDisplayNumber(),
                requestManager -> {
                    final List<PipelineStage> loaded = requestManager.loadPipelineStages(build);
                    SwingUtilities.invokeLater(() -> showStages(loaded));
                }
        ).queue();
    }

    private void showStages(@NotNull List<PipelineStage> loaded) {
        if (loaded.isEmpty()) {
            cardLayout.show(root, CARD_UNAVAILABLE);
            return;
        }
        stages = loaded;
        populateStageTree(loaded);
        cardLayout.show(root, CARD_CONTENT);

        // auto-select first stage
        if (stageTreeRoot.getChildCount() > 0) {
            final javax.swing.tree.TreePath first = new javax.swing.tree.TreePath(
                    new Object[]{stageTreeRoot, stageTreeRoot.getChildAt(0)});
            stageTree.setSelectionPath(first);
        }
    }

    private void populateStageTree(@NotNull List<PipelineStage> stageList) {
        stageTreeRoot.removeAllChildren();
        for (final PipelineStage stage : stageList) {
            stageTreeRoot.add(new DefaultMutableTreeNode(stage));
        }
        stageTreeModel.reload();
    }

    private void loadStepsForStage(@NotNull PipelineStage stage) {
        stepsHeader.setText("Steps — " + stage.getName());
        stepsContainer.removeAll();
        stepsContainer.add(new JLabel("Loading steps…") {{
            setBorder(JBUI.Borders.empty(8));
        }});
        stepsContainer.revalidate();
        stepsContainer.repaint();

        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading steps for stage: " + stage.getName(),
                requestManager -> {
                    final List<PipelineStep> steps = requestManager.loadPipelineSteps(build, stage.getId());
                    final PipelineStage withSteps = stage.toBuilder().steps(new ArrayList<>(steps)).build();
                    // update the stage in our list so OpenStageLogAction sees the steps
                    stages = stages.stream()
                            .map(s -> s.getId().equals(stage.getId()) ? withSteps : s)
                            .toList();
                    if (selectedStage != null && selectedStage.getId().equals(stage.getId())) {
                        selectedStage = withSteps;
                    }
                    SwingUtilities.invokeLater(() -> populateSteps(withSteps, steps));
                }
        ).queue();
    }

    private void populateSteps(@NotNull PipelineStage stage, @NotNull List<PipelineStep> steps) {
        stepsContainer.removeAll();
        if (steps.isEmpty()) {
            stepsContainer.add(new JLabel("No steps found for this stage") {{
                setBorder(JBUI.Borders.empty(8));
                setForeground(JBUI.CurrentTheme.Label.disabledForeground());
            }});
        } else {
            for (final PipelineStep step : steps) {
                final Path cached = logCache.get(step.getId());
                final StepPreviewRow row = new StepPreviewRow(project, build, step, cached);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                // cache the path once the row loads it
                stepsContainer.add(row);
                if (cached == null) {
                    // hook: after the row loads, register the path back in our cache
                    new javax.swing.Timer(200, e -> {
                        final Path loaded = row.getCachedLogPath();
                        if (loaded != null) {
                            logCache.putIfAbsent(step.getId(), loaded);
                            allTempFiles.add(loaded);
                        }
                    }) {{
                        setRepeats(false);
                    }}.start();
                }
            }
        }
        stepsContainer.revalidate();
        stepsContainer.repaint();
    }

    private JComponent buildLoadingCard() {
        final JLabel label = new JLabel("Loading pipeline stages…", AllIcons.General.Balloon, SwingConstants.CENTER);
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        final JPanel p = new JPanel(new GridBagLayout());
        p.add(label);
        return p;
    }

    private JComponent buildUnavailableCard() {
        final JLabel label = new JLabel(
                "Pipeline Stage View plugin not installed, or this is not a pipeline job",
                AllIcons.General.Warning, SwingConstants.CENTER);
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        final JPanel p = new JPanel(new GridBagLayout());
        p.add(label);
        return p;
    }

    private JComponent buildContentCard() {
        // Stage tree with toolbar
        final DefaultActionGroup stageGroup = new DefaultActionGroup();
        stageGroup.add(new OpenStageLogAction(this));
        final ActionToolbar stageToolbar = ActionManager.getInstance()
                .createActionToolbar("JenkinsPipelineStages", stageGroup, true);
        stageToolbar.setTargetComponent(stageTree);

        final JPanel stagesPanel = new JPanel(new BorderLayout());
        stagesPanel.add(stageToolbar.getComponent(), BorderLayout.NORTH);
        stagesPanel.add(ScrollPaneFactory.createScrollPane(stageTree), BorderLayout.CENTER);

        // Steps panel with header
        final JPanel stepsOuter = new JPanel(new BorderLayout());
        stepsOuter.add(stepsHeader, BorderLayout.NORTH);
        stepsOuter.add(ScrollPaneFactory.createScrollPane(stepsContainer), BorderLayout.CENTER);

        final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stagesPanel, stepsOuter);
        split.setDividerLocation(220);
        split.setOneTouchExpandable(true);
        split.setBorder(JBUI.Borders.empty());

        return split;
    }

    public static void show(@NotNull Project project, @NotNull Build build) {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(PipelineToolWindowFactory.TOOL_WINDOW_ID);
        if (toolWindow == null) return;

        final PipelinePanel panel = new PipelinePanel(project, build);
        GuiUtil.showInToolWindow(toolWindow, panel.getComponent(), panel,
                build.getDisplayNumber());
        panel.loadStages();
    }

    @Override
    public void dispose() {
        for (final Path path : allTempFiles) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }
}
