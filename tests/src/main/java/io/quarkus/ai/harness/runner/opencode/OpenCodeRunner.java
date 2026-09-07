package io.quarkus.ai.harness.runner.opencode;

import tools.jackson.databind.JsonNode;
import io.quarkus.ai.harness.runner.AbstractRunner;
import io.quarkus.ai.harness.runner.AgentRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.quarkus.ai.harness.runner.opencode.OpenCodeSessionExporter.exportSessions;

public class OpenCodeRunner extends AbstractRunner implements AgentRunner {

    private int streamToolCount;

    public OpenCodeRunner(String aiCmd, String provider, String model, Path skillPath, String strategy, int timeoutSeconds,
            String prompt, String skillArgs, boolean sanitize) {
        super(aiCmd, provider, model, skillPath, strategy, timeoutSeconds, prompt, skillArgs, sanitize);
    }

    /**
     * Run the opencode agent against the given project directory. Streams structured JSON output to console in real-time.
     *
     * @param projectDir the project to migrate
     * @param outputDir where to store run artifacts (logs, session, etc.)
     * @param runName prefix for output files (e.g. "spring-rest-api_anthropic_full")
     */
    @Override
    public RunOutput run(Path projectDir, Path outputDir, String runName) throws IOException, InterruptedException {

        streamToolCount = 0;

        // Create the output directories where files will be exported
        Files.createDirectories(outputDir);

        // Copy the SKILL from the skillPath resolver to the local opencode skill directory
        Path projectSkillsPath = Path.of(".opencode", "skills").toAbsolutePath();
        Files.createDirectories(projectSkillsPath);
        copySkills(skillPath, projectSkillsPath);

        // Create the configuration file with:
        // permissions: allow
        writeOpenCodeConfiguration();

        // Use the user's prompt or the one to be used for the migration test
        var userPrompt = prompt.isEmpty() ? generateMigrationPrompt() : prompt;

        List<String> cmd = new ArrayList<>();
        // Wrap with script to provide a pseudo-TTY — without it, opencode suppresses stdout output
        cmd.addAll(List.of("script", "-q", "/dev/null"));
        cmd.add(aiCmd);
        cmd.add("run");
        cmd.addAll(List.of("--format", "json"));
        cmd.addAll(List.of("--dir", projectDir.toString()));
        cmd.addAll(List.of("--title", runName)); // Use as title the name of the run session
        addModelArgs(cmd);

        cmd.add(userPrompt);

        System.out.println("  ai cwd:     " + projectDir);
        System.out.println("  output dir: " + outputDir);
        System.out.println("  run name:   " + runName);
        System.out.println("  ai cmd:   " + cmd);
        System.out.println();

        Path logFile = outputDir.resolve(runName + ".json.log");
        Path prettyFile = outputDir.resolve(runName + ".pretty.md");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

        Instant start = Instant.now();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to start opencode: " + e.getMessage());
            return new RunOutput(-1, Duration.ZERO, null, logFile.toString());
        }

        System.out.println("  opencode pid:  " + process.pid());
        System.out.println("─".repeat(60));

        Thread readerThread;
        try (var logWriter = new BufferedWriter(new FileWriter(logFile.toFile()));
                var prettyWriter = new BufferedWriter(new FileWriter(prettyFile.toFile()))) {
            readerThread = Thread.startVirtualThread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Write raw JSONL to log
                        synchronized (logWriter) {
                            logWriter.write(line);
                            logWriter.newLine();
                            logWriter.flush();
                        }

                        // Parse and print human-readable summary
                        try {
                            JsonNode event = JSON.readTree(line);
                            printEvent(event, prettyWriter);
                        } catch (Exception e) {
                            // Not JSON (e.g. script command noise), print as-is
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
                System.out.println("\n  ⏰ TIMEOUT after " + timeoutSeconds + "s — killing opencode");
                // Kill the entire process tree (children first), not just the direct process,
                // otherwise child processes survive SIGKILL and keep running
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            readerThread.join(5000);

            String summary = "\n" + "─".repeat(60) + "\n" +
                    "  opencode exit: " + exitCode + "  duration: " + duration.toSeconds() + "s";
            printBoth(summary, prettyWriter);

            // Find the session files matching the title id and export them
            var sessionFiles = exportSessions(runName, outputDir, sanitize);

            return new RunOutput(exitCode, duration, sessionFiles, logFile.toString());
        }
    }

    @Override
    protected void printEvent(JsonNode event, BufferedWriter prettyWriter) {
        String type = event.path("type").asText("");

        switch (type) {
            case "step_start" -> printBoth("  ┌── step", prettyWriter);

            case "step_finish" -> {
                JsonNode part = event.path("part");
                String reason = part.path("reason").asText("");
                JsonNode tokens = part.path("tokens");
                long totalTokens = tokens.path("total").asLong(0);
                double cost = part.path("cost").asDouble(0);
                String info = "  └── step end (" + reason + ")";
                if (totalTokens > 0) {
                    info += String.format("  [tokens: %d, cost: $%.4f]", totalTokens, cost);
                }
                printBoth(info, prettyWriter);
            }

            case "text" -> {
                String text = event.path("part").path("text").asText("");
                if (!text.isBlank()) {
                    printBoth("  │ " + text, prettyWriter);
                }
            }

            case "tool_use" -> {
                streamToolCount++;
                JsonNode part = event.path("part");
                String toolName = part.path("tool").asText("");
                JsonNode state = part.path("state");
                String status = state.path("status").asText("");
                JsonNode input = state.path("input");
                String title = state.path("title").asText("");

                String line = switch (toolName) {
                    case "bash" -> {
                        String command = input.path("command").asText("");
                        if (command.length() > 120)
                            command = command.substring(0, 117) + "...";
                        yield "  │ 🔧 bash: " + command;
                    }
                    case "edit" -> "  │ 🔧 edit: " + input.path("filePath").asText(title);
                    case "write" -> "  │ 🔧 write: " + input.path("filePath").asText(title);
                    case "read" -> "  │ 🔧 read: " + input.path("filePath").asText(title);
                    case "glob" -> "  │ 🔧 glob: " + input.path("pattern").asText("");
                    case "grep" -> "  │ 🔧 grep: " + input.path("pattern").asText("");
                    case "skill" -> "  │ 🔧 skill: " + input.path("name").asText("");
                    default -> "  │ 🔧 " + toolName + (!title.isEmpty() ? ": " + title : "");
                };

                if ("error".equals(status)) {
                    line = "  │ ❌ " + toolName + " error";
                }

                printBoth(line, prettyWriter);
            }

            // Ignore other event types
        }
    }

    @Override
    public AgentRunner.UsageStats extractUsage(List<String> sessionFiles) {
        if (sessionFiles == null) {
            return new AgentRunner.UsageStats(0, 0.0, 0, "unknown");
        }

        long totalTokens = 0;
        double totalCost = 0.0;
        int apiCalls = 0;
        String actualModel = "unknown";

        for (String sessionFile : sessionFiles) {
            if (sessionFile == null) {
                continue;
            }
            try {
                JsonNode root = JSON.readTree(Files.readString(Path.of(sessionFile)));
                JsonNode messages = root.path("messages");
                for (JsonNode message : messages) {
                    JsonNode info = message.path("info");
                    if ("assistant".equals(info.path("role").asText())) {
                        totalTokens += info.path("tokens").path("total").asLong(0);
                        totalCost += info.path("cost").asDouble(0.0);
                        apiCalls++;

                        if ("unknown".equals(actualModel)) {
                            String provider = info.path("providerID").asText("?");
                            String m = info.path("modelID").asText("?");
                            actualModel = provider + "/" + m;
                        }
                    }
                }
            } catch (IOException e) {
                // session file not found or unreadable
            }
        }

        return new AgentRunner.UsageStats(totalTokens, totalCost, apiCalls, streamToolCount, actualModel);
    }

    @Override
    public ReviewOutput review(String migrationSessionFile, Path projectDir, Path outputDir,
            String runName, Path skillPath,
            Map<String, Boolean> checkResults) throws InterruptedException, IOException {
        if (migrationSessionFile == null) {
            return new ReviewOutput("No migration session available for review.", new UsageStats(0, 0, 0, "unknown"));
        }

        // Build check summary for the prompt
        var checkSummary = new StringBuilder();
        checkResults.forEach((check, passed) ->
                checkSummary.append("  ").append(passed ? "✅" : "❌").append(" ").append(check).append("\n"));

        String prompt = """
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

        var sessionId = extractSessionId(migrationSessionFile);
        if (sessionId.isBlank()) {
            return new ReviewOutput("No session id found.", new UsageStats(0, 0, 0, "unknown"));
        }

        List<String> cmd = new ArrayList<>();
        cmd.addAll(List.of("script", "-q", "/dev/null"));
        cmd.add(aiCmd);
        cmd.add("run");
        cmd.addAll(List.of("--format", "json"));
        cmd.addAll(List.of("-s", sessionId, "--fork"));
        addModelArgs(cmd);

        cmd.add(prompt);

        System.out.println("  ai cmd:   " + cmd);
        System.out.println();
        System.out.println("  ── Skill Review ──────────────────────────────────────");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

        Instant start = Instant.now();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to start opencode: " + e.getMessage());
            return new ReviewOutput("", null);
        }

        System.out.println("  opencode pid:  " + process.pid());
        System.out.println("─".repeat(60));

        var reviewText = new StringBuilder();
        var rawLog = new StringBuilder();
        Path reviewFile = outputDir.resolve(runName + ".review.md");

        Thread readerThread = Thread.startVirtualThread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rawLog.append(line).append("\n");
                    try {
                        JsonNode event = JSON.readTree(line);
                        String eventType = event.path("type").asText("");
                        if ("text".equals(eventType)) {
                            String text = event.path("part").path("text").asText("");
                            if (!text.isBlank() && text.contains("Here is the review")) {
                                reviewText.append(text);
                                System.out.print(text);
                                System.out.flush();
                            }
                        } else {
                            System.out.flush();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
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

        Duration duration = Duration.between(start, Instant.now());

        //TODO: iterate through the list
        UsageStats reviewUsage = extractUsage(Collections.singletonList(migrationSessionFile));

        // Save review to file
        String review = reviewText.toString().trim();
        Files.writeString(reviewFile, review);

        System.out.println();
        System.out.printf("  Review: %ds, %d tokens, $%.4f%n",
                duration.toSeconds(), reviewUsage.totalTokens(), reviewUsage.totalCost());
        System.out.println("  Saved:  " + reviewFile);
        System.out.println("  ─────────────────────────────────────────────────────");

        return new ReviewOutput(review, reviewUsage);
    }

    @Override
    protected void addModelArgs(List<String> cmd) {
        boolean hasProvider = provider != null && !provider.isBlank();
        boolean hasModel = model != null && !model.isBlank();

        if (hasProvider && hasModel) {
            cmd.add("-m");
            cmd.add(provider + "/" + model);
        } else if (hasModel) {
            cmd.add("-m");
            cmd.add(model);
        }
    }

    String extractSessionId(String sessionFilePath) {

        // Pattern matches "ses_" followed by one or more alphanumeric characters
        Pattern pattern = Pattern.compile("(ses_[a-zA-Z0-9]+)");
        Matcher matcher = pattern.matcher(sessionFilePath);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }
}
