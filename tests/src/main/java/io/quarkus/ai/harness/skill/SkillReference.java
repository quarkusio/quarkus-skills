package io.quarkus.ai.harness.skill;

/**
 * Skill definition.
 *
 * @param name      the skill name from the project configuration (e.g. {@code spring-boot-to-quarkus})
 * @param url       the original URL when the skill was fetched from a remote repository, or {@code null} for local skills
 * @param localPath the resolved local directory path where the skill files reside
 */
public record SkillReference(
        String name,
        String url,
        String localPath
) {
    /**
     * {@code true} when the skill was resolved from a remote URL.
     */
    public boolean isRemote() {
        return url != null && !url.isBlank();
    }
}