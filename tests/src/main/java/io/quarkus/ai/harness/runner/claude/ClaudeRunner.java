package io.quarkus.ai.harness.runner.claude;

import tools.jackson.databind.JsonNode;
import io.quarkus.ai.harness.runner.AbstractRunner;
import io.quarkus.ai.harness.runner.AgentRunner;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Claude Code CLI agent against a project directory with a migration skill.
 * Uses --output-format stream-json for structured streaming output.
 * The skill content is embedded directly in the prompt since Claude Code
 * in prompt mode (-p) does not load skills from files.
 */
public class ClaudeRunner extends AbstractRunner implements AgentRunner {

    private String sessionId;

    public ClaudeRunner(String aiCmd, String provider, String model, Path skillPath, String strategy,
            int timeoutSeconds, String prompt, String skillArgs, boolean sanitize) {
        super(aiCmd, provider, model, skillPath, strategy, timeoutSeconds, prompt, skillArgs, sanitize);
    }

    @Override
    public RunOutput run(Path projectDir, Path outputDir, String runName) throws IOException, InterruptedException {
        Files.createDirectories(outputDir);

        String userPrompt = buildClaudePrompt();

        List<String> cmd = new ArrayList<>();
        cmd.add(aiCmd);
        cmd.addAll(List.of("-p", userPrompt));
        addModelArgs(cmd);
        cmd.addAll(List.of("--output-format", "stream-json"));
        cmd.add("--verbose");
        cmd.add("--dangerously-skip-permissions");

        System.out.println("  ai cwd:     " + projectDir);
        System.out.println("  output dir: " + outputDir);
        System.out.println("  run name:   " + runName);
        System.out.println("  ai cmd:   " + cmd);
        System.out.println();

        Path logFile = outputDir.resolve(runName + ".json.log");
        Path prettyFile = outputDir.resolve(runName + ".pretty.md");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
                .redirectErrorStream(true);

        Instant start = Instant.now();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to start claude: " + e.getMessage());
            return new RunOutput(-1, Duration.ZERO, null, logFile.toString());
        }

        System.out.println("  claude pid:  " + process.pid());
        System.out.println("─".repeat(60));

        Thread readerThread;
        try (var logWriter = new BufferedWriter(new FileWriter(logFile.toFile()));
                var prettyWriter = new BufferedWriter(new FileWriter(prettyFile.toFile()))) {
            readerThread = Thread.startVirtualThread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (logWriter) {
                            logWriter.write(line);
                            logWriter.newLine();
                            logWriter.flush();
                        }

                        try {
                            JsonNode event = JSON.readTree(line);
                            printEvent(event, prettyWriter);
                            captureSessionId(event);
                        } catch (Exception e) {
                            if (!line.isBlank()) {
                                printBoth("  │ " + line, prettyWriter);
                            }
                        }
                    }
                } catch (IOException e) {
                    // process closed stream
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            Duration duration = Duration.between(start, Instant.now());

            int exitCode;
            if (!finished) {
                System.out.println("\n  ⏰ TIMEOUT after " + timeoutSeconds + "s — killing claude");
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            readerThread.join(5000);

            String summary = "\n" + "─".repeat(60) + "\n" +
                    "  claude exit: " + exitCode + "  duration: " + duration.toSeconds() + "s";
            printBoth(summary, prettyWriter);

            return new RunOutput(exitCode, duration, Collections.singletonList(logFile.toString()), logFile.toString());
        }
    }

    private String buildClaudePrompt() throws IOException {
        var parts = new ArrayList<String>();

        // Claude has no --skill flag; skill content must always be embedded in the prompt
        Path skillMd = skillPath.resolve("SKILL.md");
        if (Files.exists(skillMd)) {
            String skillContent = Files.readString(skillMd);
            skillContent = substituteSkillArgs(skillContent);
            parts.add("<skill-instructions>\n" + skillContent + "\n</skill-instructions>");
        }

        String instruction = prompt.isEmpty() ? generateMigrationPrompt() : prompt;
        parts.add(instruction);
        return String.join("\n\n", parts);
    }

    /**
     * Substitute skill argument placeholders ($ARGUMENTS, $0, $1, $tool, etc.)
     * with actual values from the skillArgs string.
     *
     * Parses the YAML frontmatter to discover named arguments, then maps
     * positional values from skillArgs (space-separated) to both positional
     * ($0, $1) and named ($tool, $format) references.
     */
    private String substituteSkillArgs(String skillContent) {
        if (skillArgs.isBlank()) return skillContent;

        String[] argValues = skillArgs.trim().split("\\s+");

        // Parse argument names from frontmatter
        List<String> argNames = parseArgumentNames(skillContent);

        // Replace $ARGUMENTS with the full args string
        String result = skillContent.replace("$ARGUMENTS", skillArgs.trim());

        // Replace positional ($0, $1, ...) and named ($tool, $format, ...) references
        for (int i = 0; i < argValues.length; i++) {
            result = result.replace("$" + i, argValues[i]);
            if (i < argNames.size()) {
                result = result.replace("$" + argNames.get(i), argValues[i]);
            }
        }

        return result;
    }

    private List<String> parseArgumentNames(String skillContent) {
        var names = new ArrayList<String>();
        // Extract frontmatter between --- markers
        if (!skillContent.startsWith("---")) return names;
        int endIdx = skillContent.indexOf("---", 3);
        if (endIdx < 0) return names;
        String frontmatter = skillContent.substring(3, endIdx);

        boolean inArguments = false;
        for (String line : frontmatter.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("arguments:")) {
                // Check for inline list: arguments: tool format
                String inline = trimmed.substring("arguments:".length()).trim();
                if (!inline.isEmpty()) {
                    for (String name : inline.split("\\s+")) {
                        names.add(name.trim());
                    }
                    return names;
                }
                inArguments = true;
            } else if (inArguments) {
                if (trimmed.startsWith("- ")) {
                    names.add(trimmed.substring(2).trim());
                } else if (!trimmed.isEmpty()) {
                    break;
                }
            }
        }
        return names;
    }

    private void captureSessionId(JsonNode event) {
        String type = event.path("type").asText("");
        if ("system".equals(type) || "result".equals(type)) {
            String sid = event.path("session_id").asText("");
            if (!sid.isEmpty()) {
                sessionId = sid;
            }
        }
    }

    @Override
    protected void printEvent(JsonNode event, BufferedWriter prettyWriter) {
        String type = event.path("type").asText("");

        switch (type) {
            case "assistant" -> {
                JsonNode content = event.path("message").path("content");
                if (content.isArray()) {
                    for (JsonNode block : content) {
                        String blockType = block.path("type").asText("");
                        if ("text".equals(blockType)) {
                            String text = block.path("text").asText("");
                            if (!text.isBlank()) {
                                printBoth("  │ " + text, prettyWriter);
                            }
                        } else if ("tool_use".equals(blockType)) {
                            String toolName = block.path("name").asText("");
                            JsonNode input = block.path("input");

                            String line = switch (toolName) {
                                case "Bash" -> {
                                    String command = input.path("command").asText("");
                                    if (command.length() > 120)
                                        command = command.substring(0, 117) + "...";
                                    yield "  │ 🔧 bash: " + command;
                                }
                                case "Edit" -> "  │ 🔧 edit: " + input.path("file_path").asText("");
                                case "Write" -> "  │ 🔧 write: " + input.path("file_path").asText("");
                                case "Read" -> "  │ 🔧 read: " + input.path("file_path").asText("");
                                case "Agent" -> "  │ 🔧 agent: " + input.path("description").asText("");
                                default -> "  │ 🔧 " + toolName;
                            };
                            printBoth(line, prettyWriter);
                        }
                    }
                }
            }

            case "result" -> {
                JsonNode modelUsageNode = event.path("modelUsage");
                long totalInput = 0, totalOutput = 0, totalCacheRead = 0, totalCacheWrite = 0;
                double totalCostUsd = 0;

                if (modelUsageNode.isObject() && modelUsageNode.size() > 0) {
                    for (var entry : modelUsageNode.properties()) {
                        String modelName = entry.getKey();
                        JsonNode mu = entry.getValue();
                        long inp = mu.path("inputTokens").asLong(0);
                        long out = mu.path("outputTokens").asLong(0);
                        long cr = mu.path("cacheReadInputTokens").asLong(0);
                        long cw = mu.path("cacheCreationInputTokens").asLong(0);
                        double cost = mu.path("costUSD").asDouble(0);
                        totalInput += inp;
                        totalOutput += out;
                        totalCacheRead += cr;
                        totalCacheWrite += cw;
                        totalCostUsd += cost;

                        printBoth(String.format(
                                "  │ %s  [in: %d, out: %d, cache-r: %d, cache-w: %d, cost: $%.4f]",
                                modelName, inp, out, cr, cw, cost), prettyWriter);
                    }
                } else {
                    JsonNode usage = event.path("usage");
                    totalInput = usage.path("input_tokens").asLong(0);
                    totalOutput = usage.path("output_tokens").asLong(0);
                    totalCacheRead = usage.path("cache_read_input_tokens").asLong(0);
                    totalCacheWrite = usage.path("cache_creation_input_tokens").asLong(0);
                    totalCostUsd = event.path("total_cost").asDouble(event.path("cost_usd").asDouble(0));
                }

                long total = totalInput + totalOutput + totalCacheRead + totalCacheWrite;
                String info = String.format(
                        "  └── result  [tokens: %d (in: %d, out: %d, cache-r: %d, cache-w: %d), cost: $%.4f]",
                        total, totalInput, totalOutput, totalCacheRead, totalCacheWrite, totalCostUsd);
                printBoth(info, prettyWriter);
            }

            // Ignore other event types (system, user, tool_result, etc.)
        }
    }

    @Override
    public UsageStats extractUsage(List<String> sessionFiles) {
        if (sessionFiles == null) {
            return new UsageStats(0, 0.0, 0, "unknown");
        }

        long inputTotal = 0, outputTotal = 0, thinkingTotal = 0, cacheReadTotal = 0, cacheWriteTotal = 0;
        double totalCost = 0.0;
        int apiCalls = 0;
        int toolCalls = 0;
        String actualModel = model != null ? model : "unknown";
        List<ModelUsage> modelUsages = new ArrayList<>();

        for (String sessionFile : sessionFiles) {
            if (sessionFile == null)
                continue;
            try {
                List<String> lines = Files.readAllLines(Path.of(sessionFile));
                for (String line : lines) {
                    try {
                        JsonNode event = JSON.readTree(line);
                        String type = event.path("type").asText("");

                        if ("assistant".equals(type)) {
                            apiCalls++;
                            JsonNode content = event.path("message").path("content");
                            if (content.isArray()) {
                                for (JsonNode block : content) {
                                    if ("tool_use".equals(block.path("type").asText(""))) {
                                        toolCalls++;
                                    }
                                }
                            }
                        } else if ("result".equals(type)) {
                            JsonNode modelUsageNode = event.path("modelUsage");
                            if (modelUsageNode.isObject() && modelUsageNode.size() > 0) {
                                for (var entry : modelUsageNode.properties()) {
                                    String modelName = entry.getKey();
                                    JsonNode mu = entry.getValue();
                                    long muInput = mu.path("inputTokens").asLong(0);
                                    long muOutput = mu.path("outputTokens").asLong(0);
                                    long muCacheRead = mu.path("cacheReadInputTokens").asLong(0);
                                    long muCacheWrite = mu.path("cacheCreationInputTokens").asLong(0);
                                    double muCost = mu.path("costUSD").asDouble(0);

                                    inputTotal += muInput;
                                    outputTotal += muOutput;
                                    cacheReadTotal += muCacheRead;
                                    cacheWriteTotal += muCacheWrite;
                                    totalCost += muCost;

                                    modelUsages.add(new ModelUsage(
                                            modelName, muInput, muOutput, muCacheRead, muCacheWrite, muCost));
                                }
                            } else {
                                JsonNode usage = event.path("usage");
                                inputTotal += usage.path("input_tokens").asLong(0);
                                outputTotal += usage.path("output_tokens").asLong(0);
                                cacheReadTotal += usage.path("cache_read_input_tokens").asLong(0);
                                cacheWriteTotal += usage.path("cache_creation_input_tokens").asLong(0);

                                totalCost = event.path("total_cost_usd").asDouble(
                                        event.path("total_cost").asDouble(
                                                event.path("cost_usd").asDouble(0)));
                            }

                            thinkingTotal += event.path("usage").path("output_tokens_details")
                                    .path("thinking_tokens").asLong(0);

                            String m = event.path("model").asText("");
                            if (!m.isEmpty())
                                actualModel = m;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }

        long totalTokens = inputTotal + outputTotal + cacheReadTotal + cacheWriteTotal;
        return new UsageStats(totalTokens, totalCost, apiCalls, toolCalls, actualModel,
                inputTotal, outputTotal, thinkingTotal, cacheReadTotal, cacheWriteTotal, modelUsages);
    }

    @Override
    protected void addModelArgs(List<String> cmd) {
        boolean hasModel = model != null && !model.isBlank();
        if (hasModel) {
            cmd.add("--model");
            cmd.add(model);
        }
    }

    @Override
    public ReviewOutput review(String sessionFile, Path projectDir, Path outputDir,
            String runName, Path skillPath,
            Map<String, Boolean> checkResults) throws IOException, InterruptedException {
        if (sessionId == null || sessionId.isBlank()) {
            return new ReviewOutput("No Claude session available for review.", new UsageStats(0, 0, 0, "unknown"));
        }

        var checkSummary = new StringBuilder();
        checkResults.forEach((check, passed) -> checkSummary.append("  ").append(passed ? "✅" : "❌").append(" ").append(check).append("\n"));

        String reviewPrompt = """
                You just completed a migration of a Spring Boot project to Quarkus. \
                Review the migration session above and evaluate how the skill instructions performed.

                Check results:
                %s
                Based on this migration run, write a brief review covering:

                1. **What went well** — which parts of the skill worked smoothly
                2. **What went wrong** — any errors, retries, or failed checks and why
                3. **Skill improvement suggestions** — concrete changes to the SKILL.md that would \
                   help future migrations (missing instructions, wrong mappings, unclear steps, etc.)
                4. **Rating** — rate the skill 1-5 for this migration (5 = perfect, no issues)

                Be specific and actionable. Reference actual files and errors from the migration. \
                Read the current skill file at %s to see what instructions were given.

                Write your review as markdown.""".formatted(checkSummary.toString(), skillPath.resolve("SKILL.md"));

        List<String> cmd = new ArrayList<>();
        cmd.add(aiCmd);
        cmd.addAll(List.of("--resume", sessionId));
        cmd.addAll(List.of("-p", reviewPrompt));
        addModelArgs(cmd);
        cmd.addAll(List.of("--output-format", "stream-json"));
        cmd.add("--dangerously-skip-permissions");

        System.out.println("  ai cmd:   " + cmd);
        System.out.println();
        System.out.println("  ── Skill Review ──────────────────────────────────────────────────────");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(Path.of("/dev/null").toFile()))
                .redirectErrorStream(true);

        Instant start = Instant.now();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to start claude for review: " + e.getMessage());
            return new ReviewOutput("", new UsageStats(0, 0, 0, "unknown"));
        }

        System.out.println("  claude pid:  " + process.pid());
        System.out.println("─".repeat(60));

        var reviewText = new StringBuilder();
        Path reviewFile = outputDir.resolve(runName + ".review.md");

        Thread readerThread = Thread.startVirtualThread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JsonNode event = JSON.readTree(line);
                        String eventType = event.path("type").asText("");
                        if ("assistant".equals(eventType)) {
                            JsonNode content = event.path("message").path("content");
                            if (content.isArray()) {
                                for (JsonNode block : content) {
                                    if ("text".equals(block.path("type").asText(""))) {
                                        String text = block.path("text").asText("");
                                        reviewText.append(text);
                                        System.out.print(text);
                                        System.out.flush();
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        });

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        readerThread.join(5000);

        String review = reviewText.toString().trim();
        Files.writeString(reviewFile, review);

        System.out.println();
        System.out.println("  SKILL Review saved:  " + reviewFile);
        System.out.println("  ─────────────────────────────────────────────────────────");

        return new ReviewOutput(review, new UsageStats(0, 0, 0, "unknown"));
    }
}