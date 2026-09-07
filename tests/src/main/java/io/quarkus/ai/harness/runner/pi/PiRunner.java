package io.quarkus.ai.harness.runner.pi;

import tools.jackson.databind.JsonNode;
import io.quarkus.ai.harness.runner.AbstractRunner;
import io.quarkus.ai.harness.runner.AgentRunner;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Runs the pi coding agent against a project directory with a migration skill.
 * Uses --mode json for structured streaming output.
 */
public class PiRunner extends AbstractRunner implements AgentRunner {

    private int streamToolCount;

    public PiRunner(String aiCmd, String provider, String model, Path skillPath, String strategy, int timeoutSeconds, String prompt, String skillArgs, boolean sanitize) {
        super(aiCmd, provider, model, skillPath, strategy, timeoutSeconds, prompt, skillArgs, sanitize);
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
            try (var reader = new BufferedReader(new FileReader(sessionFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JsonNode entry = JSON.readTree(line);
                        if ("message".equals(entry.path("type").asText())) {
                            JsonNode msg = entry.path("message");
                            if ("assistant".equals(msg.path("role").asText())) {
                                JsonNode usage = msg.path("usage");
                                totalTokens += usage.path("totalTokens").asLong(0);
                                totalCost += usage.path("cost").path("total").asDouble(0.0);
                                apiCalls++;

                                if ("unknown".equals(actualModel)) {
                                    String provider = msg.path("provider").asText("?");
                                    String m = msg.path("model").asText("?");
                                    actualModel = provider + "/" + m;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException e) {
                // session file not found or unreadable
            }
        }

        return new AgentRunner.UsageStats(totalTokens, totalCost, apiCalls, streamToolCount, actualModel);
    }

    @Override
    protected void printEvent(JsonNode event, BufferedWriter prettyWriter) {
        String type = event.path("type").asText("");

        switch (type) {
            case "turn_start" -> printBoth("  ┌── turn", prettyWriter);

            case "turn_end" -> printBoth("  └── turn end", prettyWriter);

            case "message_start" -> {
                String role = event.path("message").path("role").asText("");
                if ("assistant".equals(role)) {
                    printBoth("  │ 🤖 assistant:", prettyWriter);
                }
            }

            case "message_end" -> {
                JsonNode msg = event.path("message");
                if ("assistant".equals(msg.path("role").asText(""))) {
                    JsonNode usage = msg.path("usage");
                    long tokens = usage.path("totalTokens").asLong(0);
                    double cost = usage.path("cost").path("total").asDouble(0);
                    if (tokens > 0) {
                        printBoth(String.format("  │    [tokens: %d, cost: $%.4f]", tokens, cost), prettyWriter);
                    }
                }
            }

            case "message_update" -> {
                JsonNode ae = event.path("assistantMessageEvent");
                String aeType = ae.path("type").asText("");

                switch (aeType) {
                    case "text_delta" -> printBothRaw(ae.path("delta").asText(""), prettyWriter);
                    case "text_end" -> printBoth("", prettyWriter);
                    default -> {
                    }
                }
            }

            case "tool_execution_start" -> {
                streamToolCount++;
                String toolName = event.path("toolName").asText("");
                JsonNode args = event.path("args");

                String line = switch (toolName) {
                    case "bash" -> {
                        String command = args.path("command").asText("");
                        if (command.length() > 120)
                            command = command.substring(0, 117) + "...";
                        yield "  │ 🔧 bash: " + command;
                    }
                    case "edit" -> {
                        String path = args.path("path").asText("");
                        int edits = args.path("edits").size();
                        yield "  │ 🔧 edit: " + path + " (" + edits + " edit" + (edits != 1 ? "s" : "") + ")";
                    }
                    case "write" -> "  │ 🔧 write: " + args.path("path").asText("");
                    case "read" -> "  │ 🔧 read: " + args.path("path").asText("");
                    default -> "  │ 🔧 " + toolName;
                };
                printBoth(line, prettyWriter);
            }

            case "tool_execution_end" -> {
                JsonNode result = event.path("result");
                if (result.path("isError").asBoolean(false)) {
                    printBoth("  │ ❌ " + event.path("toolName").asText("") + " error", prettyWriter);
                }
            }

            // Ignore: session, agent_start, thinking_*, etc.
        }
    }

    @Override
    protected void addModelArgs(List<String> cmd) {
        boolean hasProvider = provider != null && !provider.isBlank();
        boolean hasModel = model != null && !model.isBlank();

        if (hasProvider && hasModel) {
            cmd.add("--provider");
            cmd.add(provider);
            cmd.add("--model");
            cmd.add(model);
        } else if (hasModel) {
            cmd.add("--model");
            cmd.add(model);
        }
    }

    /**
     * Run the migration pi agent against the given project directory.
     * Streams structured JSON output to console in real-time.
     *
     * @param projectDir the project to migrate (pi works in this directory)
     * @param outputDir  where to store run artifacts (logs, session, etc.)
     * @param runName    prefix for output files (e.g. "spring-rest-api_anthropic_full")
     */
    public RunOutput run(Path projectDir, Path outputDir, String runName) throws IOException, InterruptedException {
        streamToolCount = 0;
        Files.createDirectories(outputDir);
        Path sessionDir = Files.createTempDirectory("pi-session-");

        // Use the user's prompt or the one to be used for the migration test
        var userPrompt = prompt.isEmpty() ? generateMigrationPrompt() : prompt;

        List<String> cmd = new ArrayList<>();
        // Pi requires a pseudo-TTY — use `script -q /dev/null` to provide one
        cmd.addAll(List.of("script", "-q", "/dev/null"));
        cmd.add(aiCmd);
        cmd.add("--print");
        cmd.add("--mode");
        cmd.add("json");
        cmd.add("--no-skills");
        cmd.add("--no-prompt-templates");
        cmd.add("--skill");
        cmd.add(skillPath.toAbsolutePath().toString());
        cmd.add("--session-dir");
        cmd.add(sessionDir.toAbsolutePath().toString());

        addModelArgs(cmd);

        cmd.add(userPrompt);

        Path logFile = outputDir.resolve(runName + ".json.log");
        Path prettyFile = outputDir.resolve(runName + ".pretty.md");

        System.out.println("  ai cwd:     " + projectDir);
        System.out.println("  output dir: " + outputDir);
        System.out.println("  run name:   " + runName);
        System.out.println();

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

        Instant start = Instant.now();
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            System.err.println("  ERROR: Failed to start pi: " + e.getMessage());
            return new RunOutput(-1, Duration.ZERO, null, logFile.toString());
        }

        System.out.println("  pi pid:  " + process.pid());
        System.out.println("─".repeat(60));

        // Parse JSON stream in a virtual thread, print human-readable output,
        // write raw JSONL to .migration.log, and pretty output to .migration-pretty.log.
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
                System.out.println("\n  ⏰ TIMEOUT after " + timeoutSeconds + "s — killing pi");
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            readerThread.join(5000);

            String summary = "\n" + "─".repeat(60) + "\n" +
                    "  pi exit: " + exitCode + "  duration: " + duration.toSeconds() + "s";
            printBoth(summary, prettyWriter);

            // Find session file
            String sessionFile = null;
            try (var stream = Files.walk(sessionDir)) {
                sessionFile = stream
                        .filter(p -> p.toString().endsWith(".jsonl"))
                        .map(Path::toString)
                        .sorted()
                        .reduce((a, b) -> b)
                        .orElse(null);
            }

            // Copy session file into the output directory
            if (sessionFile != null) {
                Path sessionCopy = outputDir.resolve(runName + ".session.jsonl");
                Files.copy(Path.of(sessionFile), sessionCopy, StandardCopyOption.REPLACE_EXISTING);
            }

            //TODO: iterate through the list
            return new RunOutput(exitCode, duration, Collections.singletonList(sessionFile), logFile.toString());
        }
    }

    /**
     * Fork the migration session and ask pi to review the migration and suggest skill improvements.
     * Runs as a separate session so cost/tokens don't mix with the migration.
     *
     * @param migrationSessionFile the session file from the migration run
     * @param projectDir the work directory (for storing the review output)
     * @param skillPath the skill that was used (so pi can read and suggest changes)
     * @param checkResults summary of which checks passed/failed
     * @return the review text and usage stats
     */
    public ReviewOutput review(String migrationSessionFile, Path projectDir, Path outputDir,
                               String runName, Path skillPath,
                               Map<String, Boolean> checkResults) throws IOException, InterruptedException {
        if (migrationSessionFile == null) {
            return new ReviewOutput("No migration session available for review.", new UsageStats(0, 0, 0, "unknown"));
        }

        Path reviewFile = outputDir.resolve(runName + ".review.md");
        Path reviewSessionDir = Files.createTempDirectory("pi-review-session-");

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

        List<String> cmd = new ArrayList<>();
        cmd.addAll(List.of("script", "-q", "/dev/null"));
        cmd.add(aiCmd);
        cmd.addAll(List.of("--print", "--mode", "json"));
        cmd.addAll(List.of("--no-skills", "--no-prompt-templates"));
        cmd.addAll(List.of("--fork", migrationSessionFile));
        cmd.addAll(List.of("--session-dir", reviewSessionDir.toAbsolutePath().toString()));

        addModelArgs(cmd);

        cmd.add(prompt);

        System.out.println();
        System.out.println("  ── Skill Review ──────────────────────────────────────");

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);

        Instant start = Instant.now();
        Process process = pb.start();

        // Collect the review text from text_delta events
        var reviewText = new StringBuilder();
        var rawLog = new StringBuilder();

        Thread readerThread = Thread.startVirtualThread(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rawLog.append(line).append("\n");
                    try {
                        JsonNode event = JSON.readTree(line);
                        String eventType = event.path("type").asText("");
                        if ("message_update".equals(eventType)) {
                            JsonNode ae = event.path("assistantMessageEvent");
                            if ("text_delta".equals(ae.path("type").asText(""))) {
                                String delta = ae.path("delta").asText("");
                                reviewText.append(delta);
                                System.out.print(delta);
                                System.out.flush();
                            } else if ("text_end".equals(ae.path("type").asText(""))) {
                                System.out.println();
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {}
        });

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        readerThread.join(5000);

        Duration duration = Duration.between(start, Instant.now());

        // Extract usage from the review session
        String reviewSessionFile = null;
        try (var stream = Files.walk(reviewSessionDir)) {
            reviewSessionFile = stream
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .map(Path::toString)
                    .sorted()
                    .reduce((a, b) -> b)
                    .orElse(null);
        }
        //TODO: iterate through the list
        UsageStats reviewUsage = extractUsage(Collections.singletonList(reviewSessionFile));

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
}
