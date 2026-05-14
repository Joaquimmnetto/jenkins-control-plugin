package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.codinjutsu.tools.jenkins.model.Build;
import org.codinjutsu.tools.jenkins.model.BuildStatusEnum;
import org.codinjutsu.tools.jenkins.view.JobDetailPanel;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class ShowJobBuildLogActionTest {

    private final JobDetailPanel jobDetailPanel = mock(JobDetailPanel.class);
    private final AnActionEvent event = mock(AnActionEvent.class);
    private final Presentation presentation = mock(Presentation.class);

    private final ShowJobBuildLogAction action = new ShowJobBuildLogAction(jobDetailPanel);

    @Before
    public void setUp() {
        when(event.getPresentation()).thenReturn(presentation);
    }

    @Test
    public void updateVisibleWhenBuildSelected() {
        when(jobDetailPanel.getSelectedBuild()).thenReturn(Optional.of(aBuild()));
        action.update(event);
        verify(presentation).setVisible(true);
    }

    @Test
    public void updateHiddenWhenNoBuildSelected() {
        when(jobDetailPanel.getSelectedBuild()).thenReturn(Optional.empty());
        action.update(event);
        verify(presentation).setVisible(false);
    }

    private static Build aBuild() {
        return Build.builder()
                .url("http://jenkins/job/my-job/1/")
                .number(1)
                .status(BuildStatusEnum.FAILURE)
                .building(false)
                .timestamp(new Date(0L))
                .build();
    }
}