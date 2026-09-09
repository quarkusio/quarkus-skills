package io.quarkus.ai.harness.checks;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Verifies a project by executing different checks declared here and able to:
 * - Compile,
 * - Run tests,
 * - Start the java application,
 * - Probe endpoints
 *
 * Each check can be enabled if you declare them within the Project.yaml file under the field: "checks:"
 * using the name defined part of the method: runCheck()
 *
 */
public class ProjectVerifier {

    private static final int APP_PORT = 18080;

    private final Path projectDir;

    public ProjectVerifier(Path projectDir) {
        this.projectDir = projectDir;
    }

    /**
     * Check if the project compiles successfully.
     */
    public boolean builds() {
        return runMaven("compile", "-DskipTests") == 0;
    }

    /**
     * Check if all tests pass.
     */
    public boolean testsPass() {
        return runMaven("test") == 0;
    }

    /**
     * Check that no Spring Framework dependencies remain in pom.xml.
     */
    public boolean noSpringDeps() {
        return !fileContains(projectDir.resolve("pom.xml"), "org.springframework");
    }

    /**
     * Check that Quarkus dependencies are present in pom.xml.
     */
    public boolean hasQuarkus() {
        return fileContains(projectDir.resolve("pom.xml"), "io.quarkus");
    }

    /**
     * Check that the application starts up and responds to HTTP requests.
     */
    public boolean startsUp() {
        Path startupLog = projectDir.resolve(".startup.log");
        Process process = null;
        try {
            process = startApp();
            if (!waitForReady(process)) {
                dumpStartupLog(startupLog, "app failed to start");
                return false;
            }
            return true;
        } catch (Exception e) {
            dumpStartupLog(startupLog, e.getMessage());
            return false;
        } finally {
            stopApp(process);
        }
    }

    /**
     * Start the app, hit each endpoint defined in project.yaml, and verify responses.
     */
    public boolean smokeTest(List<EndpointCheck> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            System.out.println("      no endpoints defined — skipping");
            return true;
        }

        Path startupLog = projectDir.resolve(".startup.log");
        Process process = null;
        try {
            process = startApp();

            if (!waitForReady(process)) {
                dumpStartupLog(startupLog, "app failed to start");
                return false;
            }

            System.out.println("Calling the endpoints to validate");
            boolean allPassed = true;
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()) {
                for (EndpointCheck ep : endpoints) {
                    if (!process.isAlive()) {
                        System.out.println("      app process crashed. Cannot access the endpoint " + ep.path());
                        return false;
                    }
                    boolean ok = testEndpoint(client, ep);
                    if (!ok) allPassed = false;
                }
            }
            return allPassed;

        } catch (Exception e) {
            dumpStartupLog(startupLog, e.getMessage());
            return false;
        } finally {
            stopApp(process);
        }
    }

    private void dumpStartupLog(Path logFile, String reason) {
        System.err.println("    starts-up FAILED: " + reason);
        dumpLogFile(logFile, ".startup.log (maven)");
    }

    private void dumpLogFile(Path logFile, String label) {
        System.err.println("    " + label + " (" + logFile + "):");
        try {
            if (!Files.exists(logFile)) {
                System.err.println("      (file not found)");
                return;
            }
            Files.readAllLines(logFile).forEach(line -> System.err.println("      " + line));
        } catch (IOException e) {
            System.err.println("      (could not read log: " + e.getMessage() + ")");
        }
    }

    /**
     * Check that no Thymeleaf references remain.
     */
    public boolean noThymeleaf() {
        if (fileContains(projectDir.resolve("pom.xml"), "thymeleaf")) {
            return false;
        }
        try (var stream = Files.walk(projectDir.resolve("src"))) {
            return stream
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".html") || p.toString().endsWith(".xml"))
                    .noneMatch(p -> fileContains(p, "thymeleaf") || fileContains(p, "th:"));
        } catch (IOException e) {
            return true; // no src dir = no thymeleaf
        }
    }

    /**
     * Run a specific named check.
     */
    public boolean runCheck(String checkName, CheckConfig checkConfig) {
        return switch (checkName) {
            case "builds" -> builds();
            case "tests-pass" -> testsPass();
            case "no-spring-deps" -> noSpringDeps();
            case "has-quarkus" -> hasQuarkus();
            case "starts-up" -> startsUp();
            case "smoke-test" -> smokeTest(checkConfig.endpoints());
            case "no-thymeleaf" -> noThymeleaf();
            default -> throw new IllegalArgumentException("Unknown check: " + checkName);
        };
    }

    // -- app lifecycle helpers --

    private Process startApp() throws IOException {
        Path startupLog = projectDir.resolve(".startup.log");
        ProcessBuilder pb = new ProcessBuilder(
                getMvnCmd(), "quarkus:dev",
                "-Dquarkus.http.port=" + APP_PORT,
                "-Dquarkus.devservices.enabled=false",
                "-Dquarkus.analytics.disabled=true",
                "-Dquarkus.console.enabled=false"
        ).directory(projectDir.toFile())
         .redirectErrorStream(true)
         .redirectOutput(startupLog.toFile());

        return pb.start();
    }

    private boolean waitForReady(Process process) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            if (!process.isAlive()) return false;
            if (httpOk("http://localhost:" + APP_PORT + "/q/health/ready") ||
                httpOk("http://localhost:" + APP_PORT + "/")) {
                return true;
            }
        }
        return false;
    }

    private void stopApp(Process process) {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try {
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    // -- endpoint testing --

    private boolean testEndpoint(HttpClient client, EndpointCheck ep) {
        String url = "http://localhost:" + APP_PORT + ep.path();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10));

            switch (ep.effectiveMethod()) {
                case "POST" -> reqBuilder
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(ep.body() != null ? ep.body() : ""));
                case "PUT" -> reqBuilder
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(ep.body() != null ? ep.body() : ""));
                case "DELETE" -> reqBuilder.DELETE();
                default -> reqBuilder.GET();
            }

            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            int actual = response.statusCode();
            int expected = ep.effectiveExpectedStatus();
            boolean statusOk = actual == expected;
            boolean bodyOk = ep.bodyContains() == null || ep.bodyContains().isBlank()
                    || response.body().contains(ep.bodyContains());

            if (!statusOk) {
                System.out.printf("      FAIL %s %s → %d (expected %d)%n",
                        ep.effectiveMethod(), ep.path(), actual, expected);
            } else if (!bodyOk) {
                System.out.printf("      FAIL %s %s → body missing '%s'%n",
                        ep.effectiveMethod(), ep.path(), ep.bodyContains());
            } else {
                System.out.printf("      OK   %s %s → %d%n",
                        ep.effectiveMethod(), ep.path(), actual);
            }

            return statusOk && bodyOk;

        } catch (Exception e) {
            System.out.printf("      FAIL %s %s → %s%n",
                    ep.effectiveMethod(), ep.path(), e.getMessage());
            return false;
        }
    }

    // -- maven / file helpers --

    private int runMaven(String... goals) {
        try {
            var cmd = new java.util.ArrayList<String>();
            cmd.add(getMvnCmd());
            cmd.add("-B");
            cmd.addAll(java.util.List.of(goals));

            Process p = new ProcessBuilder(cmd)
                    .directory(projectDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(projectDir.resolve(".maven-" + goals[0] + ".log").toFile())
                    .start();

            boolean done = p.waitFor(300, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    private String getMvnCmd() {
        Path wrapper = projectDir.resolve("mvnw");
        if (Files.isExecutable(wrapper)) {
            return wrapper.toAbsolutePath().toString();
        }
        return "mvn";
    }

    private static boolean fileContains(Path file, String text) {
        try {
            return Files.readString(file).contains(text);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean httpOk(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }
}