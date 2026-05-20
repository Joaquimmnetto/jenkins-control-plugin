package org.codinjutsu.tools.jenkins.logic;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import org.codinjutsu.tools.jenkins.model.PipelineStage;
import org.codinjutsu.tools.jenkins.model.PipelineStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipelineJsonParser {

    @NotNull
    public List<PipelineStage> parseStages(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        final JsonObject root = Jsoner.deserialize(json, new JsonObject());
        final JsonArray stagesArray = getArray(root, "stages");
        if (stagesArray.isEmpty()) {
            return Collections.emptyList();
        }
        final List<PipelineStage> stages = new ArrayList<>(stagesArray.size());
        for (Object element : stagesArray) {
            if (element instanceof JsonObject stageJson) {
                stages.add(parseStage(stageJson));
            }
        }
        return stages;
    }

    @NotNull
    private PipelineStage parseStage(@NotNull JsonObject json) {
        return PipelineStage.builder()
                .id(getString(json, "id"))
                .name(getString(json, "name"))
                .status(getString(json, "status"))
                .startTimeMillis(getLong(json, "startTimeMillis"))
                .durationMillis(getLong(json, "durationMillis"))
                .build();
    }

    @NotNull
    public List<PipelineStep> parseSteps(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        final JsonObject root = Jsoner.deserialize(json, new JsonObject());
        final JsonArray stepsArray = getArray(root, "stageFlowNodes");
        if (stepsArray.isEmpty()) {
            return Collections.emptyList();
        }
        final List<PipelineStep> steps = new ArrayList<>(stepsArray.size());
        for (Object element : stepsArray) {
            if (element instanceof JsonObject stepJson) {
                steps.add(parseStep(stepJson));
            }
        }
        return steps;
    }

    @NotNull
    private PipelineStep parseStep(@NotNull JsonObject json) {
        return PipelineStep.builder()
                .id(getString(json, "id"))
                .name(getString(json, "name"))
                .status(getString(json, "status"))
                .durationMillis(getLong(json, "durationMillis"))
                .parameterDescription(getStringOrNull(json, "parameterDescription"))
                .build();
    }

    @NotNull
    public String parseLogText(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        final JsonObject root = Jsoner.deserialize(json, new JsonObject());
        return getString(root, "text");
    }

    public boolean parseHasMore(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        final JsonObject root = Jsoner.deserialize(json, new JsonObject());
        return Boolean.TRUE.equals(root.get("hasMore"));
    }

    @NotNull
    private static JsonArray getArray(@NotNull JsonObject json, @NotNull String key) {
        return (JsonArray) json.getOrDefault(key, new JsonArray());
    }

    @NotNull
    private static String getString(@NotNull JsonObject json, @NotNull String key) {
        final Object value = json.get(key);
        return value instanceof String s ? s : "";
    }

    @Nullable
    private static String getStringOrNull(@NotNull JsonObject json, @NotNull String key) {
        final Object value = json.get(key);
        return value instanceof String s ? s : null;
    }

    private static long getLong(@NotNull JsonObject json, @NotNull String key) {
        final Object value = json.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
