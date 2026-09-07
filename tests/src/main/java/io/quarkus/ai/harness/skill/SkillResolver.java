package io.quarkus.ai.harness.skill;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a skill reference to a local directory path.
 *
 * <p>Supported formats for {@code skillRef}:
 * <ul>
 *   <li>Skill name (e.g. {@code spring-boot-to-quarkus}) — looks up in the local skills/ directory</li>
 *   <li>GitHub URL with {@code /tree/} (e.g. {@code https://github.com/org/repo/tree/branch/subpath}) — clones and navigates</li>
 *   <li>GitHub repo URL (e.g. {@code https://github.com/org/repo}) with {@code explicitBranch}
 *       set to {@code branch/subpath} — clones the branch and navigates to the subpath</li>
 * </ul>
 *
 * <p>Branch disambiguation: GitHub URLs are parsed by splitting on the first {@code /} after
 * {@code /tree/}, which fails when the branch name itself contains a {@code /} and there is also
 * a subpath (e.g. {@code tree/feature/my-branch/skills/my-skill}). In that case pass the branch
 * name explicitly via {@code explicitBranch} and the subpath will be derived from the URL.
 *
 * <p>When the URL has no {@code /tree/} path, {@code explicitBranch} is parsed as
 * {@code branch[/subpath]} (split on the first {@code /}).
 *
 * <p>Remote clones are cached in {@code downloadDir} for the duration of the test run
 * and cleaned up with {@code mvn clean}.
 */
public class SkillResolver {

    private final Path skillsBaseDir;
    private final Path downloadDir;
    private final Map<String, Path> cache = new ConcurrentHashMap<>();

    public SkillResolver(Path skillsBaseDir, Path downloadDir) {
        this.skillsBaseDir = skillsBaseDir;
        this.downloadDir = downloadDir;
    }

    /**
     * @param skillRef skill name, GitHub URL, or GitHub URL with {@code #branch/subpath}
     */
    public Path resolve(String skillRef) throws IOException, InterruptedException {
        if (skillRef == null || skillRef.isBlank()) {
            throw new IllegalArgumentException("Skill reference cannot be empty");
        }

        int hashIdx = skillRef.indexOf('#');
        if (hashIdx >= 0) {
            return resolve(skillRef.substring(0, hashIdx), skillRef.substring(hashIdx + 1));
        }

        return resolve(skillRef, null);
    }

    /**
     * @param skillRef      skill name or GitHub URL
     * @param explicitBranch optional branch override — only needed when the branch name contains
     *                       {@code /} and the URL also has a subpath; ignored otherwise
     */
    public Path resolve(String skillRef, String explicitBranch) throws IOException, InterruptedException {
        if (skillRef == null || skillRef.isBlank()) {
            throw new IllegalArgumentException("Skill reference cannot be empty");
        }

        if (isUrl(skillRef)) {
            return resolveFromUrl(skillRef, blankToNull(explicitBranch));
        }

        Path p = Path.of(skillRef);
        if (p.isAbsolute()) {
            return p;
        }

        Path resolved = skillsBaseDir.resolve(skillRef);
        if (Files.isDirectory(resolved)) {
            return resolved;
        }

        // Fall back to CWD-relative resolution
        Path cwdResolved = Path.of("").toAbsolutePath().resolve(skillRef);
        if (Files.isDirectory(cwdResolved)) {
            return cwdResolved;
        }

        return resolved;
    }

    private Path resolveFromUrl(String url, String explicitBranch) throws IOException, InterruptedException {
        String cacheKey = url + "|" + nullToEmpty(explicitBranch);
        if (cache.containsKey(cacheKey)) {
            System.out.println("  Using cached skill: " + cache.get(cacheKey));
            return cache.get(cacheKey);
        }

        String cloneUrl = url;
        String branch = null;
        String subPath = "";

        if (url.contains("/tree/")) {
            int treeIdx = url.indexOf("/tree/");
            cloneUrl = url.substring(0, treeIdx);
            String rest = url.substring(treeIdx + "/tree/".length()); // "branch[/subpath]"

            if (explicitBranch != null) {
                // Derive subpath from URL using the known branch name
                branch = explicitBranch;
                subPath = rest.startsWith(explicitBranch + "/")
                        ? rest.substring(explicitBranch.length() + 1)
                        : "";
            } else {
                // Best-effort: split on first '/' — ambiguous when branch contains '/'
                int slashIdx = rest.indexOf('/');
                if (slashIdx >= 0) {
                    branch = rest.substring(0, slashIdx);
                    subPath = rest.substring(slashIdx + 1);
                } else {
                    branch = rest;
                }
            }
        } else if (explicitBranch != null) {
            // No /tree/ in URL: treat explicitBranch as "branch[/subpath]"
            int slashIdx = explicitBranch.indexOf('/');
            if (slashIdx >= 0) {
                branch = explicitBranch.substring(0, slashIdx);
                subPath = explicitBranch.substring(slashIdx + 1);
            } else {
                branch = explicitBranch;
            }
        }

        String dirKey = cloneUrl + (branch != null ? "@" + branch : "");
        String dirName = dirKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path cloneDir = downloadDir.resolve(dirName);

        if (!Files.exists(cloneDir)) {
            System.out.println("  Fetching skill from: " + cloneUrl +
                    (branch != null ? " (branch: " + branch + ")" : ""));
            Files.createDirectories(downloadDir);

            List<String> cloneCmd = new ArrayList<>(List.of("git", "clone", "--depth", "1"));
            if (branch != null) cloneCmd.addAll(List.of("--branch", branch));
            cloneCmd.addAll(List.of(cloneUrl, cloneDir.toString()));

            Process p = new ProcessBuilder(cloneCmd)
                    .redirectErrorStream(true)
                    .start();

            boolean done = p.waitFor(120, TimeUnit.SECONDS);
            if (!done || p.exitValue() != 0) {
                throw new IOException("Failed to clone skill from: " + cloneUrl);
            }
            System.out.println("  Cloned to: " + cloneDir);
        } else {
            System.out.println("  Reusing cached clone: " + cloneDir);
        }

        Path skillPath = subPath.isBlank() ? cloneDir : cloneDir.resolve(subPath);
        if (!Files.isDirectory(skillPath)) {
            String available = availableSkillDirs(cloneDir);
            throw new IOException(
                    "Skill path not found: " + skillPath + "\n" +
                    "  The repo was cloned to: " + cloneDir + "\n" +
                    "  Available directories with SKILL.md: " + available + "\n" +
                    "  Tip: if your branch name contains '/', use -Dpi.skill.branch=<branch>.");
        }

        cache.put(cacheKey, skillPath);
        return skillPath;
    }

    private String availableSkillDirs(Path cloneDir) {
        try (var stream = Files.walk(cloneDir, 2)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("SKILL.md")))
                    .map(p -> cloneDir.relativize(p).toString())
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .map(s -> "[" + s + "]")
                    .orElse("(no directories with SKILL.md found — check repo structure)");
        } catch (IOException e) {
            return "(could not list: " + e.getMessage() + ")";
        }
    }

    private boolean isUrl(String ref) {
        return ref.startsWith("https://") || ref.startsWith("http://") || ref.startsWith("git@");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
