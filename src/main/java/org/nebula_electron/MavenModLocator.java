package org.nebula_electron;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/**
 * Reads mod coordinates from a list file and resolves each one to a jar path in a local Maven repo.
 *
 * <p>The list file is pointed to by the JVM argument {@code -Dmercator.mod.list=<path>} and
 * contains one {@code group:artifact:version} coordinate per line. The repo root is read from
 * {@code mercator.repo.root} (defaults to {@code ../../common/modstore} relative to the working
 * directory).
 *
 * <p>The Minecraft version comes from {@code mercator.minecraft.version} and controls which
 * source directory ({@code java-mc1.21/}, etc.) is compiled at build time.
 *
 * <p>Resolved jars are handed off to NeoForge by {@link MavenModLocatorRegistrar}.
 */
public class MavenModLocator {

    private final Path repoRoot;
    private final String minecraftVersion;

    /**
     * Reads config from system properties.
     *
     * <p>Repo root comes from {@code mercator.repo.root}; falls back to
     * {@code ../../common/modstore} if unset. MC version comes from
     * {@code mercator.minecraft.version}; defaults to {@code 1.20.1}.
     */
    public MavenModLocator() {
        String repo = System.getProperty("mercator.repo.root");
        this.repoRoot = (repo != null && !repo.isBlank())
                ? Paths.get(repo).toAbsolutePath().normalize()
                : Paths.get("../../common/modstore").toAbsolutePath().normalize();
        this.minecraftVersion = System.getProperty("mercator.minecraft.version", "1.20.1");
    }

    /** Returns the Minecraft version from {@code mercator.minecraft.version}. */
    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Reads the mod list and resolves each coordinate to an existing jar path.
     *
     * <p>Blank lines and lines starting with {@code #} are skipped. Coordinates that fail to
     * parse or whose jar is missing from the repo are skipped with a warning.
     *
     * @return list of resolved, existing jar paths
     */
    public List<Path> resolveModJars() {
        System.out.println("[Mercator] repoRoot=" + repoRoot);

        String modListPath = getJvmArg("mercator.mod.list");
        System.out.println("[Mercator] modListFile=" + modListPath);

        Path modList = (modListPath != null && !modListPath.isBlank())
                ? Paths.get(modListPath).toAbsolutePath().normalize()
                : null;

        System.out.println("[Mercator] exists=" + (modList != null && Files.exists(modList)));

        if (modList == null || !Files.isRegularFile(modList)) {
            System.out.println("[Mercator] No mod list found");
            return List.of();
        }

        System.out.println("[Mercator] Online");
        List<Path> jars = new ArrayList<>();

        try {
            for (String line : Files.readAllLines(modList)) {
                String coord = line.trim();
                if (coord.isEmpty() || coord.startsWith("#")) continue;

                MavenCoordinate coordinate = MavenCoordinate.parse(coord);
                if (coordinate == null) {
                    System.out.println("[Mercator] Invalid coordinate: " + coord);
                    continue;
                }

                Path jarPath = coordinate.resolveIn(repoRoot);
                if (!Files.isRegularFile(jarPath)) {
                    System.out.println("[Mercator] Missing jar: " + jarPath);
                    continue;
                }

                jars.add(jarPath);
                System.out.println("[Mercator] Found: " + coord);
                System.out.println("[Mercator] Path " + jarPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed reading mod list", e);
        }

        System.out.println("[Mercator] Done");
        return jars;
    }

    /**
     * Looks up a {@code -D} JVM argument by key.
     *
     * @param key property name without the {@code -D} prefix
     * @return the value after the {@code =}, or null if the argument isn't set
     */
    private String getJvmArg(String key) {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-D" + key + "="))
                .map(arg -> arg.substring(arg.indexOf('=') + 1))
                .findFirst().orElse(null);
    }
}
