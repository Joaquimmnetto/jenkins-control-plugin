package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.model.JobType;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class FocusOnItemActionTest {

    private final BrowserPanel browserPanel = mock(BrowserPanel.class);
    private final AnActionEvent actionEvent = mock(AnActionEvent.class);
    private final Presentation presentation = mock(Presentation.class);
    private final FocusOnItemAction action = new FocusOnItemAction(browserPanel);

    @Before
    public void setUp() {
        when(actionEvent.getPresentation()).thenReturn(presentation);
    }

    @Test
    public void updateHiddenWhenNoJobSelected() {
        when(browserPanel.getSelectedJob()).thenReturn(null);
        action.update(actionEvent);
        verify(presentation).setVisible(false);
        verify(presentation).setEnabled(false);
    }

    @Test
    public void updateHiddenForRegularJob() {
        when(browserPanel.getSelectedJob()).thenReturn(jobOf(JobType.JOB));
        action.update(actionEvent);
        verify(presentation).setVisible(false);
        verify(presentation).setEnabled(false);
    }

    @Test
    public void updateVisibleForFolder() {
        when(browserPanel.getSelectedJob()).thenReturn(jobOf(JobType.FOLDER));
        action.update(actionEvent);
        verify(presentation).setVisible(true);
        verify(presentation).setEnabled(true);
    }

    @Test
    public void updateVisibleForMultiBranchPipeline() {
        when(browserPanel.getSelectedJob()).thenReturn(jobOf(JobType.MULTI_BRANCH));
        action.update(actionEvent);
        verify(presentation).setVisible(true);
        verify(presentation).setEnabled(true);
    }

    @Test
    public void actionPerformedDelegatesToBrowserPanel() {
        Job folder = jobOf(JobType.FOLDER);
        when(browserPanel.getSelectedJob()).thenReturn(folder);
        action.actionPerformed(actionEvent);
        verify(browserPanel).focusOnJob(folder);
    }

    @Test
    public void actionPerformedDoesNothingWhenNoJobSelected() {
        when(browserPanel.getSelectedJob()).thenReturn(null);
        action.actionPerformed(actionEvent);
        verify(browserPanel, never()).focusOnJob(any());
    }

    private static Job jobOf(JobType jobType) {
        return Job.builder()
                .name("test-job")
                .fullName("test-job")
                .jobType(jobType)
                .url("http://jenkins.example.com/job/test/")
                .inQueue(false)
                .buildable(true)
                .build();
    }
}