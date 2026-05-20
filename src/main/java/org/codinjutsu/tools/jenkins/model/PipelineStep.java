package org.codinjutsu.tools.jenkins.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
@Builder
public class PipelineStep {

    @NotNull String id;
    @NotNull String name;
    @NotNull String status;
    long durationMillis;
    @Nullable String parameterDescription;
}
