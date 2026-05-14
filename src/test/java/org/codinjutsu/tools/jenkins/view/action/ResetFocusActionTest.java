package org.codinjutsu.tools.jenkins.view.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.codinjutsu.tools.jenkins.view.BrowserPanel;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class ResetFocusActionTest {

    private final BrowserPanel browserPanel = mock(BrowserPanel.class);
    private final AnActionEvent actionEvent = mock(AnActionEvent.class);
    private final Presentation presentation = mock(Presentation.class);
    private final ResetFocusAction action = new ResetFocusAction(browserPanel);

    @Before
    public void setUp() {
        when(actionEvent.getPresentation()).thenReturn(presentation);
    }

    @Test
    public void updateDisabledWhenNotFocused() {
        when(browserPanel.isFocused()).thenReturn(false);
        action.update(actionEvent);
        verify(presentation).setEnabled(false);
    }

    @Test
    public void updateEnabledWhenFocused() {
        when(browserPanel.isFocused()).thenReturn(true);
        action.update(actionEvent);
        verify(presentation).setEnabled(true);
    }

    @Test
    public void actionPerformedClearsFocusOnBrowserPanel() {
        action.actionPerformed(actionEvent);
        verify(browserPanel).clearFocusedJob();
    }
}