package org.codinjutsu.tools.jenkins.settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.codinjutsu.tools.jenkins.DoubleClickAction;
import org.codinjutsu.tools.jenkins.JenkinsAppSettings;
import org.codinjutsu.tools.jenkins.JenkinsControlBundle;
import org.codinjutsu.tools.jenkins.view.DoubleClickActionRenderer;
import org.codinjutsu.tools.jenkins.view.annotation.FormValidationPanel;
import org.codinjutsu.tools.jenkins.view.annotation.GuiField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.codinjutsu.tools.jenkins.util.GuiUtil.createLabeledComponent;
import static org.codinjutsu.tools.jenkins.view.validator.ValidatorTypeEnum.POSITIVE_INTEGER;

public class AppSettingComponent implements FormValidationPanel {
    @GuiField(validators = POSITIVE_INTEGER)
    private final JBIntSpinner buildDelay = new JBIntSpinner(0, 0, 3000);
    @GuiField(validators = POSITIVE_INTEGER)
    private final JBIntSpinner jobRefreshPeriod = new JBIntSpinner(0, 0, 120);
    @GuiField(validators = POSITIVE_INTEGER)
    private final JBIntSpinner numBuildRetries = new JBIntSpinner(0, 0, 50);
    @GuiField(validators = POSITIVE_INTEGER)
    private final JBIntSpinner buildsToLoadPerJob = new JBIntSpinner(0, 0, 100);
    private final ComboBox<DoubleClickAction> doubleClickAction = createDoubleClickActionComboBox();

    private final JBCheckBox useGreenColor = new JBCheckBox(JenkinsControlBundle.message("settings.app.useGreenColor"));
    private final JBCheckBox autoLoadBuildsOnFirstLevel = new JBCheckBox(JenkinsControlBundle.message("settings.app.autoLoadBuilds"));
    private final JBCheckBox showLogIfTriggerBuild = new JBCheckBox(JenkinsControlBundle.message("settings.app.showLogOnTrigger"));

    private final JPanel mainPanel;

    public AppSettingComponent() {

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JenkinsControlBundle.message("settings.app.buildDelay.label"),
                        createLabeledComponent(buildDelay, JenkinsControlBundle.message("settings.seconds")))
                .addLabeledComponent(JenkinsControlBundle.message("settings.app.jobRefresh.label"),
                        createLabeledComponent(jobRefreshPeriod, JenkinsControlBundle.message("settings.minutes")))
                .addLabeledComponent(JenkinsControlBundle.message("settings.app.numBuildRetries.label"),
                        numBuildRetries)
                .addLabeledComponent(JenkinsControlBundle.message("settings.app.buildsToLoad.label"),
                        buildsToLoadPerJob)
                .addLabeledComponent(JenkinsControlBundle.message("settings.app.doubleClickAction.label"),
                        doubleClickAction)
                .addComponent(useGreenColor)
                .addComponent(autoLoadBuildsOnFirstLevel)
                .addComponent(showLogIfTriggerBuild)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private static ComboBox<DoubleClickAction> createDoubleClickActionComboBox() {
        final var doubleClickAction = new ComboBox<DoubleClickAction>();
        final JBDimension size = JBUI.size(245, doubleClickAction.getPreferredSize().height);
        doubleClickAction.setPreferredSize(size);
        doubleClickAction.setEditable(false);
        doubleClickAction.setRenderer(new DoubleClickActionRenderer());
        doubleClickAction.addItem(DoubleClickAction.TRIGGER_BUILD);
        doubleClickAction.addItem(DoubleClickAction.LOAD_BUILDS);
        doubleClickAction.addItem(DoubleClickAction.SHOW_LAST_LOG);
        return doubleClickAction;
    }

    public @NotNull JPanel getPanel() {
        return mainPanel;
    }

    public JenkinsAppSettings getSetting() {
        final JenkinsAppSettings jenkinsAppSettings = new JenkinsAppSettings();
        jenkinsAppSettings.setDelay(buildDelay.getNumber());
        jenkinsAppSettings.setJobRefreshPeriod(jobRefreshPeriod.getNumber());
        jenkinsAppSettings.setNumBuildRetries(numBuildRetries.getNumber());
        jenkinsAppSettings.setBuildsToLoadPerJob(buildsToLoadPerJob.getNumber());

        jenkinsAppSettings.setUseGreenColor(useGreenColor.isSelected());
        jenkinsAppSettings.setAutoLoadBuilds(autoLoadBuildsOnFirstLevel.isSelected());
        jenkinsAppSettings.setDoubleClickAction(doubleClickAction.getItem());
        jenkinsAppSettings.setShowLogIfTriggerBuild(showLogIfTriggerBuild.isSelected());
        return jenkinsAppSettings;
    }

    public void setBuildDelay(int buildDelay) {
        this.buildDelay.setNumber(buildDelay);
    }

    public void setJobRefreshPeriod(int jobRefreshPeriod) {
        this.jobRefreshPeriod.setNumber(jobRefreshPeriod);
    }

    public void setNumBuildRetries(int numBuildRetries) {
        this.numBuildRetries.setNumber(numBuildRetries);
    }

    public void setBuildsToLoadPerJob(int buildsToLoadPerJob) {
        this.buildsToLoadPerJob.setNumber(buildsToLoadPerJob);
    }

    public void setDoubleClickAction(DoubleClickAction doubleClickAction) {
        this.doubleClickAction.setItem(doubleClickAction);
    }

    public void setUseGreenColor(boolean useGreenColor) {
        this.useGreenColor.setSelected(useGreenColor);
    }

    public void setAutoLoadBuilds(boolean autoLoadBuilds) {
        this.autoLoadBuildsOnFirstLevel.setSelected(autoLoadBuilds);
    }

    public void setShowLogIfTriggerBuild(boolean showLogIfTriggerBuild) {
        this.showLogIfTriggerBuild.setSelected(showLogIfTriggerBuild);
    }
}
