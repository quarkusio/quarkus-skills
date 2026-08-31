package com.migration.validator.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.validator.model.cldk.AnalysisResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CodeAnalyzerClient {
    private static final String VERSION = "2.4.1";
    private static final long TIMEOUT_MINUTES = 30;
    private static final String JAR_URL = "https://github.com/codellm-devkit/codeanalyzer-java/releases/download/v"
            + VERSION + "/codeanalyzer.jar";
    private static final Path DEFAULT_JAR_PATH = Paths.get(System.getProperty("user.home"), ".migration-validator",
            "tools", "codeanalyzer", VERSION, "codeanalyzer.jar");

    private final ObjectMapper objectMapper;
    private final Path jarPath;

    public CodeAnalyzerClient() {
        this(DEFAULT_JAR_PATH);
    }

    public CodeAnalyzerClient(Path jarPath) {
        this.jarPath = jarPath.toAbsolutePath();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AnalysisResult analyzeProject(Path projectRoot, int analysisLevel) throws IOException, InterruptedException {
        String rawJson = analyzeProjectRaw(projectRoot, analysisLevel);
        return objectMapper.readValue(rawJson, AnalysisResult.class);
    }

    /**
     * Runs codeanalyzer.jar and returns the raw JSON string output without
     * deserializing.
     */
    public String analyzeProjectRaw(Path projectRoot, int analysisLevel) throws IOException, InterruptedException {
        ensureJarExists();

        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-jar");
        command.add(jarPath.toString());
        command.add("--input");
        command.add(projectRoot.toAbsolutePath().toString());
        command.add("--analysis-level");
        command.add(String.valueOf(analysisLevel));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);
        Process process = processBuilder.start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> stderrFuture = executor.submit(() -> readFully(process.getErrorStream()));
        executor.shutdown();

        String stdout;
        try (InputStream inputStream = process.getInputStream()) {
            stdout = readFully(inputStream);
        }

        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("codeanalyzer timed out after " + TIMEOUT_MINUTES + " minutes");
        }

        int exitCode = process.exitValue();
        String stderr;
        try {
            stderr = stderrFuture.get();
        } catch (ExecutionException e) {
            stderr = "(failed to capture stderr: " + e.getCause().getMessage() + ")";
        }

        if (exitCode != 0) {
            throw new IOException(String.format(
                    "codeanalyzer failed with exit code %d%n%nstderr:%n%s%n%nstdout:%n%s",
                    exitCode, stderr, stdout));
        }

        return stdout;
    }

    public Path getJarPath() {
        return jarPath;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private void ensureJarExists() throws IOException {
        if (Files.exists(jarPath)) {
            return;
        }

        Files.createDirectories(jarPath.getParent());
        Path tmp = Files.createTempFile(jarPath.getParent(), "codeanalyzer-", ".tmp");
        try (InputStream inputStream = java.net.URI.create(JAR_URL).toURL().openStream()) {
            Files.copy(inputStream, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, jarPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private static String readFully(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
