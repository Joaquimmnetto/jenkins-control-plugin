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

package org.codinjutsu.tools.jenkins;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import lombok.*;
import org.codinjutsu.tools.jenkins.security.JenkinsVersion;
import org.jetbrains.annotations.NotNull;

@State(
        name = "Jenkins.Settings",
        storages = {
                @Storage(StoragePathMacros.WORKSPACE_FILE)
        }
)
public class JenkinsSettings implements PersistentStateComponent<JenkinsSettings.State> {

    public static final String JENKINS_SETTINGS_PASSWORD_KEY = "JENKINS_SETTINGS_PASSWORD_KEY";
    private State myState = new State();

    public static JenkinsSettings getSafeInstance(Project project) {
        JenkinsSettings settings = project.getService(JenkinsSettings.class);
        return settings != null ? settings : new JenkinsSettings();
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
    }

    public String getUsername() {
        return myState.getUsername();
    }

    public void setUsername(String username) {
        myState.setUsername(username);
    }

    public @NotNull String getJenkinsUrl() {
        return myState.getJenkinsUrl();
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        myState.setJenkinsUrl(jenkinsUrl);
    }

    public String getCrumbData() {
        return myState.getCrumbData();
    }

    public void setCrumbData(String crumbData) {
        myState.setCrumbData(crumbData);
    }

    public String getPassword() {
        String password = PasswordSafe.getInstance().getPassword(getPasswordCredentialAttributes());
        return StringUtil.defaultIfEmpty(password, "");
    }

    @Deprecated
    public void setPassword(String password) {
        PasswordSafe.getInstance().setPassword(getPasswordCredentialAttributes(),
                org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(password) ? password : "");
    }

    @NotNull
    private CredentialAttributes getPasswordCredentialAttributes() {
        return new CredentialAttributes(JenkinsAppSettings.class.getName(), JENKINS_SETTINGS_PASSWORD_KEY);
    }

    public String getLastSelectedView() {
        return myState.getLastSelectedView();
    }

    public void setLastSelectedView(String viewName) {
        myState.setLastSelectedView(viewName);
    }

    public boolean isSecurityMode() {
        return org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(getUsername());
    }

    public JenkinsVersion getVersion() {
        return this.myState.getJenkinsVersion();
    }

    public void setVersion(JenkinsVersion jenkinsVersion) {
        this.myState.setJenkinsVersion(jenkinsVersion);
    }

    public int getConnectionTimeout() {
        return myState.getConnectionTimeout();
    }

    public void setConnectionTimeout(int timeoutInSeconds) {
        myState.setConnectionTimeout(timeoutInSeconds);
    }

    @Data
    public static class State {
        public static final String RESET_STR_VALUE = "";

        private static final int DEFAULT_CONNECTION_TIMEOUT = 10;

        private String username = RESET_STR_VALUE;

        private String crumbData = RESET_STR_VALUE;

        private String lastSelectedView;

        private JenkinsVersion jenkinsVersion = JenkinsVersion.VERSION_1;

        private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private @NotNull String jenkinsUrl = RESET_STR_VALUE;
    }
}
