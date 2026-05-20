package org.codinjutsu.tools.jenkins.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class PipelineStage {

    @NotNull String id;
    @NotNull String name;
    @NotNull String status;
    long startTimeMillis;
    long durationMillis;

    @Builder.Default
    @NotNull List<PipelineStep> steps = new LinkedList<>();
}
