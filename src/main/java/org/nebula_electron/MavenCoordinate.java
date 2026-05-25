package org.nebula_electron;

import java.nio.file.*;

/**
 * Represents a Maven artifact coordinate in {@code group:artifact:version} form.
 *
 * @param group    the Maven group ID (e.g. {@code net.neoforged})
 * @param artifact the Maven artifact ID (e.g. {@code neoforge})
 * @param version  the artifact version string (e.g. {@code 21.1.0+1})
 */
public record MavenCoordinate(String group, String artifact, String version) {

    /**
     * Parses a colon-separated Maven coordinate string.
     *
     * @param coord a string in {@code group:artifact:version} format
     * @return the parsed coordinate, or {@code null} if fewer than three colon-separated parts are present
     */
    public static MavenCoordinate parse(String coord) {
        String[] parts = coord.split(":");
        if (parts.length < 3) return null;
        return new MavenCoordinate(parts[0], parts[1], parts[2]);
    }

    /**
     * Resolves the path to this artifact's jar inside a local Maven repository.
     *
     * <p>If the version contains {@code '+'}, a patched variant
     * ({@code <artifact>-<version>.patched.jar}) is preferred when it exists. Patched jars have
     * the {@code '+'} stripped from their version in {@code neoforge.mods.toml} so that Maven
     * version ranges resolve correctly. Patching is skipped for jars that do not need it because
     * patching a signed jar invalidates its signature.
     *
     * @param repoRoot the root directory of the local Maven repository
     * @return the resolved, normalized path to the jar file
     */
    public Path resolveIn(Path repoRoot) {
        Path base = repoRoot
                .resolve(group.replace('.', '/'))
                .resolve(artifact)
                .resolve(version);

        if (version.contains("+")) {
            System.out.println("[Mercator] Checking for patched jar for: " + this);
            Path patched = base.resolve(artifact + "-" + version + ".patched.jar").normalize();
            System.out.println("[Mercator] Patched jar path: " + patched);
            if (Files.exists(patched)) return patched;
        }

        return base.resolve(artifact + "-" + version + ".jar").normalize();
    }

    /**
     * Returns this coordinate as a {@code group:artifact:version} string.
     *
     * @return the canonical string representation of this coordinate
     */
    @Override
    public String toString() {
        return group + ":" + artifact + ":" + version;
    }
}
