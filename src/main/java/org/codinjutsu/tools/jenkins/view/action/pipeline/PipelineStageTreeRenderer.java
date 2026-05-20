package org.codinjutsu.tools.jenkins.view.action.pipeline;

import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.codinjutsu.tools.jenkins.model.PipelineStage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

class PipelineStageTreeRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof DefaultMutableTreeNode node)) return;
        final Object userObject = node.getUserObject();
        if (!(userObject instanceof PipelineStage stage)) return;

        setIcon(statusIcon(stage.getStatus()));
        append(stage.getName());
        if (stage.getDurationMillis() > 0) {
            append("  " + NlsMessages.formatDuration(stage.getDurationMillis()),
                    SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
        }
    }

    @NotNull
    private static Icon statusIcon(@NotNull String status) {
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
