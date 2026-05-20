package org.codinjutsu.tools.jenkins.view.action.pipeline;

import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import org.codinjutsu.tools.jenkins.JenkinsAppSettings;
import org.codinjutsu.tools.jenkins.logic.JenkinsBackgroundTaskFactory;
import org.codinjutsu.tools.jenkins.model.Build;
import org.codinjutsu.tools.jenkins.model.PipelineStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

class StepPreviewRow extends JPanel {

    private static final int PREVIEW_ROWS = 15;

    private final Project project;
    private final Build build;
    private final PipelineStep step;
    private final JPanel detailPanel;
    private final JBTextArea logArea;
    private boolean expanded = false;
    private boolean logLoaded = false;

    @Nullable
    private Path cachedLogPath;

    StepPreviewRow(@NotNull Project project, @NotNull Build build, @NotNull PipelineStep step,
                   @Nullable Path cachedLogPath) {
        super(new BorderLayout());
        this.project = project;
        this.build = build;
        this.step = step;
        this.cachedLogPath = cachedLogPath;

        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));

        final JPanel header = buildHeader();
        detailPanel = buildDetailPanel();
        detailPanel.setVisible(false);

        add(header, BorderLayout.NORTH);
        add(detailPanel, BorderLayout.CENTER);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleExpand();
            }
        });

        logArea = (JBTextArea) ((JScrollPane) detailPanel.getComponent(0)).getViewport().getView();
    }

    @Nullable
    Path getCachedLogPath() {
        return cachedLogPath;
    }

    void setCachedLogPath(@NotNull Path path) {
        this.cachedLogPath = path;
    }

    private JPanel buildHeader() {
        final JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(3, 6));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final JLabel expandIcon = new JLabel(AllIcons.General.ArrowRight);
        final JLabel statusIcon = new JLabel(stepStatusIcon(step.getStatus()));
        final JLabel nameLabel = new JLabel(stepDisplayName(step));
        final JLabel durationLabel = new JLabel(NlsMessages.formatDuration(step.getDurationMillis()),
                SwingConstants.RIGHT);
        durationLabel.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        durationLabel.setBorder(JBUI.Borders.emptyLeft(8));

        final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(expandIcon);
        left.add(statusIcon);
        left.add(nameLabel);

        header.add(left, BorderLayout.WEST);
        header.add(durationLabel, BorderLayout.EAST);
        header.setOpaque(false);

        header.putClientProperty("expandIcon", expandIcon);
        return header;
    }

    private JPanel buildDetailPanel() {
        final JBTextArea area = new JBTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f)));
        area.setRows(PREVIEW_ROWS);
        area.setText("Loading…");

        final JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(JBUI.Borders.empty());

        final JPanel openBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        openBar.setOpaque(false);
        final JButton openBtn = new JButton("Open in Editor", AllIcons.Actions.OpenNewTab);
        openBtn.addActionListener(e -> openLogInEditor());
        openBar.add(openBtn);

        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.emptyLeft(16));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(openBar, BorderLayout.SOUTH);
        return panel;
    }

    private void toggleExpand() {
        expanded = !expanded;
        final JPanel header = (JPanel) getComponent(0);
        final JLabel expandIcon = (JLabel) header.getClientProperty("expandIcon");
        expandIcon.setIcon(expanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
        detailPanel.setVisible(expanded);

        if (expanded && !logLoaded) {
            logLoaded = true;
            loadLogPreview();
        }
        revalidate();

        // notify parent scroll pane to re-layout
        final Container parent = getParent();
        if (parent != null) parent.revalidate();
    }

    private void loadLogPreview() {
        if (cachedLogPath != null) {
            showPreviewFromFile(cachedLogPath);
            return;
        }
        final int maxLines = JenkinsAppSettings.getSafeInstance(project).getPipelineLogLines();
        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading step log: " + step.getName(),
                requestManager -> {
                    try {
                        final Path tmp = java.nio.file.Files.createTempFile("jenkins-step-" + step.getId() + "-", ".log");
                        try (final FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                            requestManager.fetchNodeLog(build, step.getId(), fos);
                        }
                        cachedLogPath = tmp;
                        final String preview = tailLines(tmp, maxLines);
                        SwingUtilities.invokeLater(() -> logArea.setText(preview.isEmpty() ? "(no output)" : preview));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> logArea.setText("Error loading log: " + e.getMessage()));
                    }
                }
        ).queue();
    }

    private void showPreviewFromFile(@NotNull Path path) {
        final int maxLines = JenkinsAppSettings.getSafeInstance(project).getPipelineLogLines();
        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Reading cached step log",
                requestManager -> {
                    try {
                        final String preview = tailLines(path, maxLines);
                        SwingUtilities.invokeLater(() -> logArea.setText(preview.isEmpty() ? "(no output)" : preview));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> logArea.setText("Error reading log: " + e.getMessage()));
                    }
                }
        ).queue();
    }

    private void openLogInEditor() {
        if (cachedLogPath == null) {
            loadAndOpenLog();
            return;
        }
        openFile(cachedLogPath);
    }

    private void loadAndOpenLog() {
        JenkinsBackgroundTaskFactory.getInstance(project).createBackgroundTask(
                "Loading step log for editor: " + step.getName(),
                requestManager -> {
                    try {
                        final Path tmp = java.nio.file.Files.createTempFile("jenkins-step-" + step.getId() + "-", ".log");
                        try (final FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                            requestManager.fetchNodeLog(build, step.getId(), fos);
                        }
                        cachedLogPath = tmp;
                        SwingUtilities.invokeLater(() -> openFile(tmp));
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() -> logArea.setText("Error loading log: " + e.getMessage()));
                    }
                }
        ).queue();
    }

    private void openFile(@NotNull Path path) {
        final VirtualFile vf = LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(path.toFile());
        if (vf != null) {
            FileEditorManager.getInstance(project).openFile(vf, true);
        }
    }

    @NotNull
    static String tailLines(@NotNull Path path, int maxLines) throws IOException {
        final long fileSize = path.toFile().length();
        if (fileSize == 0) return "";

        try (final RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long pos = fileSize - 1;
            int linesFound = 0;
            final java.util.Deque<Byte> buffer = new java.util.ArrayDeque<>();

            // skip trailing newline at end of file so it doesn't count as an empty line
            if (pos >= 0) {
                raf.seek(pos);
                if (raf.read() == '\n') {
                    pos--;
                }
            }

            while (pos >= 0 && linesFound < maxLines) {
                raf.seek(pos);
                final byte b = raf.readByte();
                buffer.addFirst(b);
                if (b == '\n') {
                    linesFound++;
                }
                pos--;
            }

            // include any remaining partial first line
            if (pos < 0 && linesFound < maxLines) {
                // already at the start; buffer contains everything
            }

            final byte[] bytes = new byte[buffer.size()];
            int i = 0;
            for (byte b : buffer) {
                bytes[i++] = b;
            }
            final String result = new String(bytes, StandardCharsets.UTF_8);
            // strip a leading newline that was the separator we stopped on
            return result.startsWith("\n") ? result.substring(1) : result;
        }
    }

    @NotNull
    private static String stepDisplayName(@NotNull PipelineStep step) {
        final String param = step.getParameterDescription();
        if (param != null && !param.isBlank()) {
            final String truncated = param.length() > 60 ? param.substring(0, 57) + "…" : param;
            return step.getName() + ": " + truncated;
        }
        return step.getName();
    }

    @NotNull
    private static Icon stepStatusIcon(@NotNull String status) {
        return switch (status) {
            case "SUCCESS" -> AllIcons.RunConfigurations.TestPassed;
            case "FAILED", "FAILURE" -> AllIcons.RunConfigurations.TestFailed;
            case "IN_PROGRESS" -> AllIcons.Actions.Execute;
            case "PAUSED" -> AllIcons.Actions.Pause;
            case "NOT_EXECUTED", "SKIPPED" -> AllIcons.RunConfigurations.TestSkipped;
            default -> AllIcons.RunConfigurations.TestUnknown;
        };
    }
}
