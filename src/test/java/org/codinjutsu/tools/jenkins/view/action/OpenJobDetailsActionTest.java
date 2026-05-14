package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.model.JobType;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class OpenJobDetailsActionTest {

    private final Project project = mock(Project.class);
    private final BrowserPanel browserPanel = mock(BrowserPanel.class);
    private final AnActionEvent event = mock(AnActionEvent.class);
    private final Presentation presentation = mock(Presentation.class);

    private final OpenJobDetailsAction action = new OpenJobDetailsAction();

    @Before
    public void setUp() {
        final DataContext dataContext = mock(DataContext.class);
        when(dataContext.getData(PlatformDataKeys.PROJECT)).thenReturn(project);
        when(project.getService(BrowserPanel.class)).thenReturn(browserPanel);
        when(event.getPresentation()).thenReturn(presentation);
        when(event.getDataContext()).thenReturn(dataContext);
    }

    @Test
    public void updateVisibleForActualJob() {
        when(browserPanel.getSelectedJob()).thenReturn(jobOfType(JobType.JOB));
        action.update(event);
        verify(presentation).setVisible(true);
    }

    @Test
    public void updateHiddenWhenNoJobSelected() {
        when(browserPanel.getSelectedJob()).thenReturn(null);
        action.update(event);
        verify(presentation).setVisible(false);
    }

    @Test
    public void updateHiddenForFolder() {
        when(browserPanel.getSelectedJob()).thenReturn(jobOfType(JobType.FOLDER));
        action.update(event);
        verify(presentation).setVisible(false);
    }

    @Test
    public void updateHiddenForMultiBranch() {
        when(browserPanel.getSelectedJob()).thenReturn(jobOfType(JobType.MULTI_BRANCH));
        action.update(event);
        verify(presentation).setVisible(false);
    }

    private static Job jobOfType(JobType type) {
        return Job.builder().name("job").jobType(type).fullName("job").url("http://jenkins/job").build();
    }
}
