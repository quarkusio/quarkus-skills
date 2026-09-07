package io.quarkus.ai.harness.test;

import io.quarkus.ai.harness.runner.claude.ClaudeRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skill argument substitution")
class SkillArgsSubstitutionTest {

    private static final Path DUMMY_SKILL = Path.of(
            Objects.requireNonNull(SkillArgsSubstitutionTest.class.getResource("/skills/dummy-skill")).getPath());

    @Test
    @DisplayName("Named and positional args are substituted in SKILL.md content")
    void namedAndPositionalArgs() throws Exception {
        ClaudeRunner runner = new ClaudeRunner(
                "claude", null, null, DUMMY_SKILL,
                "full", 300, "Do analysis.", "mtool json", false);

        String prompt = invokeBuildClaudePrompt(runner);

        // Named args: $tool → mtool, $format → json
        assertFalse(prompt.contains("$tool"), "Expected $tool to be substituted");
        assertFalse(prompt.contains("$format"), "Expected $format to be substituted");
        assertTrue(prompt.contains("Analyze the code using `mtool` and output as `json`."),
                "Named args should be substituted in the body");

        // Positional args: $0 → mtool, $1 → json
        assertFalse(prompt.contains("$0"), "Expected $0 to be substituted");
        assertFalse(prompt.contains("$1"), "Expected $1 to be substituted");
        assertTrue(prompt.contains("First arg: mtool"), "Positional $0 should resolve to mtool");
        assertTrue(prompt.contains("Second arg: json"), "Positional $1 should resolve to json");

        // $ARGUMENTS → full string
        assertTrue(prompt.contains("All arguments: mtool json"),
                "Expected $ARGUMENTS to be the full args string");
    }

    @Test
    @DisplayName("Single arg substitutes $tool and $0")
    void singleArg() throws Exception {
        ClaudeRunner runner = new ClaudeRunner(
                "claude", null, null, DUMMY_SKILL,
                "full", 300, "Do analysis.", "mtool", false);

        String prompt = invokeBuildClaudePrompt(runner);

        assertFalse(prompt.contains("$tool"), "Expected $tool to be substituted");
        assertFalse(prompt.contains("$0"), "Expected $0 to be substituted");
        assertTrue(prompt.contains("Analyze the code using `mtool`"),
                "Single arg should substitute $tool");
        // $format and $1 remain unsubstituted (no second arg provided)
        assertTrue(prompt.contains("$format"), "Unprovided $format should remain as-is");
        assertTrue(prompt.contains("$1"), "Unprovided $1 should remain as-is");
    }

    @Test
    @DisplayName("Empty args leaves all placeholders intact")
    void emptyArgs() throws Exception {
        ClaudeRunner runner = new ClaudeRunner(
                "claude", null, null, DUMMY_SKILL,
                "full", 300, "Do analysis.", "", false);

        String prompt = invokeBuildClaudePrompt(runner);

        assertTrue(prompt.contains("$tool"), "$tool should remain when no args provided");
        assertTrue(prompt.contains("$format"), "$format should remain when no args provided");
        assertTrue(prompt.contains("$0"), "$0 should remain when no args provided");
        assertTrue(prompt.contains("$ARGUMENTS"), "$ARGUMENTS should remain when no args provided");
    }

    @Test
    @DisplayName("Prompt instruction is appended after skill content")
    void promptAppended() throws Exception {
        ClaudeRunner runner = new ClaudeRunner(
                "claude", null, null, DUMMY_SKILL,
                "full", 300, "Do analysis.", "mtool json", false);

        String prompt = invokeBuildClaudePrompt(runner);

        assertTrue(prompt.contains("<skill-instructions>"), "Should wrap skill in tags");
        assertTrue(prompt.contains("</skill-instructions>"), "Should close skill tags");
        assertTrue(prompt.contains("Do analysis."), "User prompt should be appended");

        int skillEnd = prompt.indexOf("</skill-instructions>");
        int promptStart = prompt.indexOf("Do analysis.");
        assertTrue(promptStart > skillEnd, "User prompt should come after skill instructions");
    }

    private String invokeBuildClaudePrompt(ClaudeRunner runner) throws Exception {
        Method m = ClaudeRunner.class.getDeclaredMethod("buildClaudePrompt");
        m.setAccessible(true);
        return (String) m.invoke(runner);
    }
}
