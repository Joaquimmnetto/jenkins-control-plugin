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
import org.codinjutsu.tools.jenkins.model.Job;
import org.codinjutsu.tools.jenkins.model.JobType;
import org.codinjutsu.tools.jenkins.security.JenkinsVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.function.Function;

@State(
        name = "Jenkins.Settings",
        storages = {
                @Storage(StoragePathMacros.WORKSPACE_FILE)
        }
)
public class JenkinsSettings implements PersistentStateComponent<JenkinsSettings.State> {

    public static final String JENKINS_SETTINGS_PASSWORD_KEY = "JENKINS_SETTINGS_PASSWORD_KEY";
    public static final String JENKINS_SETTINGS_GITHUB_TOKEN_KEY = "JENKINS_SETTINGS_GITHUB_TOKEN_KEY";
    public static final String DEFAULT_GITHUB_API_URL = "https://api.github.com";

    private State myState = new State();
    private Function<String, String> envLookup = System::getenv;

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

    public @NotNull String getGithubToken() {
        final String stored = PasswordSafe.getInstance().getPassword(getGithubTokenCredentialAttributes());
        if (org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(stored)) {
            return stored;
        }
        final String gh = envLookup.apply("GH_TOKEN");
        if (org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(gh)) {
            return gh;
        }
        final String github = envLookup.apply("GITHUB_TOKEN");
        return org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(github) ? github : "";
    }

    public boolean isGithubTokenStoredInSettings() {
        return org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(
                PasswordSafe.getInstance().getPassword(getGithubTokenCredentialAttributes()));
    }

    public void setGithubToken(String githubToken) {
        PasswordSafe.getInstance().setPassword(getGithubTokenCredentialAttributes(),
                org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(githubToken) ? githubToken : "");
    }

    @NotNull
    private CredentialAttributes getGithubTokenCredentialAttributes() {
        return new CredentialAttributes(JenkinsAppSettings.class.getName(), JENKINS_SETTINGS_GITHUB_TOKEN_KEY);
    }

    public @NotNull String getGithubApiUrl() {
        return org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(myState.getGithubApiUrl())
                ? myState.getGithubApiUrl() : DEFAULT_GITHUB_API_URL;
    }

    public void setGithubApiUrl(String githubApiUrl) {
        myState.setGithubApiUrl(org.codinjutsu.tools.jenkins.util.StringUtil.isNotBlank(githubApiUrl)
                ? githubApiUrl : DEFAULT_GITHUB_API_URL);
    }

    @VisibleForTesting
    void setEnvLookup(@NotNull Function<String, String> envLookup) {
        this.envLookup = envLookup;
    }

    public String getLastSelectedView() {
        return myState.getLastSelectedView();
    }

    public void setLastSelectedView(String viewName) {
        myState.setLastSelectedView(viewName);
    }

    public boolean hasFocusedJob() {
        return !StringUtil.isEmpty(myState.getFocusedJobUrl());
    }

    public void setFocusedJob(@NotNull String url, @NotNull String name,
                              @NotNull String fullName, @NotNull String jobType) {
        myState.setFocusedJobUrl(url);
        myState.setFocusedJobName(name);
        myState.setFocusedJobFullName(fullName);
        myState.setFocusedJobType(jobType);
    }

    public void clearFocusedJob() {
        myState.setFocusedJobUrl(null);
        myState.setFocusedJobName(null);
        myState.setFocusedJobFullName(null);
        myState.setFocusedJobType(null);
    }

    @NotNull
    public Job restoreFocusedJob() {
        JobType jobType;
        try {
            jobType = myState.getFocusedJobType() != null
                    ? JobType.valueOf(myState.getFocusedJobType()) : JobType.FOLDER;
        } catch (IllegalArgumentException e) {
            jobType = JobType.FOLDER;
        }
        return Job.builder()
                .name(myState.getFocusedJobName())
                .fullName(myState.getFocusedJobFullName())
                .url(myState.getFocusedJobUrl())
                .jobType(jobType)
                .build();
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

        private String focusedJobUrl;
        private String focusedJobName;
        private String focusedJobFullName;
        private String focusedJobType;

        private JenkinsVersion jenkinsVersion = JenkinsVersion.VERSION_1;

        private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private @NotNull String jenkinsUrl = RESET_STR_VALUE;
        private @NotNull String githubApiUrl = DEFAULT_GITHUB_API_URL;
    }
}
