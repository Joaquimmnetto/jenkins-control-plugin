package org.codinjutsu.tools.jenkins.logic;

import org.codinjutsu.tools.jenkins.model.PipelineStage;
import org.codinjutsu.tools.jenkins.model.PipelineStep;
import org.codinjutsu.tools.jenkins.util.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PipelineJsonParserTest {

    private PipelineJsonParser parser;

    @Before
    public void setUp() {
        parser = new PipelineJsonParser();
    }

    @Test
    public void parseStages_returnsAllStages() throws Exception {
        final String json = IOUtils.toString(
                getClass().getResourceAsStream("wfapi_describe_pipeline.json"));
        final List<PipelineStage> stages = parser.parseStages(json);

        assertThat(stages).hasSize(4);

        final PipelineStage checkout = stages.get(0);
        assertThat(checkout.getId()).isEqualTo("3");
        assertThat(checkout.getName()).isEqualTo("Checkout");
        assertThat(checkout.getStatus()).isEqualTo("SUCCESS");
        assertThat(checkout.getDurationMillis()).isEqualTo(2000L);

        final PipelineStage deploy = stages.get(2);
        assertThat(deploy.getStatus()).isEqualTo("FAILED");

        final PipelineStage notify = stages.get(3);
        assertThat(notify.getStatus()).isEqualTo("NOT_EXECUTED");
        assertThat(notify.getDurationMillis()).isEqualTo(0L);
    }

    @Test
    public void parseStages_emptyOnEmptyJson() {
        assertThat(parser.parseStages("{}")).isEmpty();
        assertThat(parser.parseStages(null)).isEmpty();
        assertThat(parser.parseStages("")).isEmpty();
    }

    @Test
    public void parseSteps_returnsAllSteps() throws Exception {
        final String json = IOUtils.toString(
                getClass().getResourceAsStream("wfapi_steps_stage.json"));
        final List<PipelineStep> steps = parser.parseSteps(json);

        assertThat(steps).hasSize(2);

        final PipelineStep sh = steps.get(0);
        assertThat(sh.getId()).isEqualTo("8");
        assertThat(sh.getName()).isEqualTo("Shell Script");
        assertThat(sh.getStatus()).isEqualTo("SUCCESS");
        assertThat(sh.getDurationMillis()).isEqualTo(2000L);
        assertThat(sh.getParameterDescription()).isEqualTo("mvn clean install");

        final PipelineStep archive = steps.get(1);
        assertThat(archive.getParameterDescription()).isNull();
    }

    @Test
    public void parseSteps_emptyOnEmptyJson() {
        assertThat(parser.parseSteps("{}")).isEmpty();
        assertThat(parser.parseSteps(null)).isEmpty();
    }

    @Test
    public void parseLogText_returnsText() throws Exception {
        final String json = IOUtils.toString(
                getClass().getResourceAsStream("wfapi_node_log.json"));
        final String text = parser.parseLogText(json);

        assertThat(text).contains("[Pipeline] sh");
        assertThat(text).contains("BUILD SUCCESS");
    }

    @Test
    public void parseHasMore_returnsFalseWhenDone() throws Exception {
        final String json = IOUtils.toString(
                getClass().getResourceAsStream("wfapi_node_log.json"));
        assertThat(parser.parseHasMore(json)).isFalse();
    }

    @Test
    public void parseHasMore_returnsTrueWhenMore() {
        final String json = "{\"text\": \"partial log\", \"hasMore\": true}";
        assertThat(parser.parseHasMore(json)).isTrue();
    }
}
