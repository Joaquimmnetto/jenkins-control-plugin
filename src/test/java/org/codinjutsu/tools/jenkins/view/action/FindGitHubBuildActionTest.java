package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.*;

public class FindGitHubBuildActionTest {

    private final Project project = mock(Project.class);
    private final BrowserPanel browserPanel = mock(BrowserPanel.class);
    private final AnActionEvent event = mock(AnActionEvent.class);
    private final Presentation presentation = mock(Presentation.class);

    private final FindGitHubBuildAction action = new FindGitHubBuildAction();

    @Before
    public void setUp() {
        final DataContext dataContext = mock(DataContext.class);
        when(dataContext.getData(PlatformDataKeys.PROJECT)).thenReturn(project);
        when(project.getService(BrowserPanel.class)).thenReturn(browserPanel);
        when(event.getPresentation()).thenReturn(presentation);
        when(event.getDataContext()).thenReturn(dataContext);
    }

    @Test
    public void disabledWhenNoJobsLoaded() {
        when(browserPanel.getAllJobs()).thenReturn(List.of());
        action.update(event);
        verify(presentation).setEnabled(false);
    }

    @Test
    public void enabledWhenJobsAreLoaded() {
        when(browserPanel.getAllJobs()).thenReturn(List.of(sampleJob()));
        action.update(event);
        verify(presentation).setEnabled(true);
    }

    @Test
    public void disabledWhenProjectMissing() {
        final DataContext empty = mock(DataContext.class);
        when(empty.getData(PlatformDataKeys.PROJECT)).thenReturn(null);
        when(event.getDataContext()).thenReturn(empty);

        action.update(event);
        verify(presentation).setEnabled(false);
    }

    private static Job sampleJob() {
        return Job.builder().name("job").fullName("job").url("http://jenkins/job").build();
    }
}
