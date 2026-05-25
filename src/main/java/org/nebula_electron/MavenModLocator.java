package org.nebula_electron;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/**
 * NeoForge {@link IModFileCandidateLocator} that resolves mod jars from a local Maven repository.
 *
 * <p>At launch the locator reads a newline-separated list of Maven coordinates
 * ({@code group:artifact:version}) from the file pointed to by the JVM argument
 * {@code -Dmercator.mod.list=<path>}. Each coordinate is looked up in the local repository whose
 * root is controlled by the system property {@code mercator.repo.root} (defaults to
 * {@code ../../common/modstore} relative to the working directory).
 *
 * <p>Jars that contain a JarJar overlay sharing the same mod ID as their outer wrapper are handled
 * transparently by {@link OverlappingJarResolver}.
 */
public class MavenModLocator implements IModFileCandidateLocator {

    private final Path repoRoot;
    private final OverlappingJarResolver overlappingJarResolver = new OverlappingJarResolver();

    /**
     * Constructs the locator.
     *
     * <p>The repository root is taken from the system property {@code mercator.repo.root}; if
     * absent or blank, it falls back to {@code ../../common/modstore} resolved from the JVM
     * working directory.
     */
    public MavenModLocator() {
        String repo = System.getProperty("mercator.repo.root");
        this.repoRoot = (repo != null && !repo.isBlank())
                ? Paths.get(repo).toAbsolutePath().normalize()
                : Paths.get("../../common/modstore").toAbsolutePath().normalize();
    }

    /**
     * Reads the mod list file and registers each valid jar with the discovery pipeline.
     *
     * <p>Lines that are blank or start with {@code #} are ignored. Coordinates that cannot be
     * parsed, or whose jar does not exist in the repository, are skipped with a console warning.
     *
     * @param context  the NeoForge launch context (unused directly, forwarded to the pipeline)
     * @param pipeline the discovery pipeline to which resolved jar contents are added
     */
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        System.out.println("[Mercator] repoRoot=" + repoRoot);

        String modListPath = getJvmArg("mercator.mod.list");
        System.out.println("[Mercator] modListFile=" + modListPath);

        Path modList = (modListPath != null && !modListPath.isBlank())
                ? Paths.get(modListPath).toAbsolutePath().normalize()
                : null;

        System.out.println("[Mercator] exists=" + (modList != null && Files.exists(modList)));

        if (modList == null || !Files.isRegularFile(modList)) {
            System.out.println("[Mercator] No mod list found");
            return;
        }

        System.out.println("[Mercator] Online");

        try {
            for (String line : Files.readAllLines(modList)) {
                processLine(line.trim(), pipeline);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed reading mod list", e);
        }

        System.out.println("[Mercator] Done");
    }

    /**
     * Processes a single coordinate line from the mod list.
     *
     * <p>Skips blank lines and comments. Resolves the coordinate to a jar path, then delegates to
     * {@link OverlappingJarResolver} before handing the result to the pipeline.
     *
     * @param coord    a trimmed line from the mod list file
     * @param pipeline the discovery pipeline to register the jar with
     */
    private void processLine(String coord, IDiscoveryPipeline pipeline) {
        if (coord.isEmpty() || coord.startsWith("#")) return;

        MavenCoordinate coordinate = MavenCoordinate.parse(coord);
        if (coordinate == null) {
            System.out.println("[Mercator] Invalid coordinate: " + coord);
            return;
        }

        Path jarPath = coordinate.resolveIn(repoRoot);
        if (!Files.isRegularFile(jarPath)) {
            System.out.println("[Mercator] Missing jar: " + jarPath);
            return;
        }

        try {
            JarContents contents = overlappingJarResolver.resolve(jarPath, coord);
            pipeline.addJarContent(contents, ModFileDiscoveryAttributes.DEFAULT.withLocator(this), IncompatibleFileReporting.ERROR);
            System.out.println("[Mercator] Added: " + coord);
            System.out.println("[Mercator] Path " + jarPath);
        } catch (Exception e) {
            System.err.println("[Mercator] Failed to register: " + jarPath);
            e.printStackTrace();
        }
    }

    /**
     * Returns the value of a JVM {@code -D} argument by key, or {@code null} if not set.
     *
     * @param key the property name without the leading {@code -D} prefix
     * @return the value after the {@code =} sign, or {@code null} if the argument is absent
     */
    private String getJvmArg(String key) {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-D" + key + "="))
                .map(arg -> arg.substring(arg.indexOf('=') + 1))
                .findFirst().orElse(null);
    }

    /**
     * Returns {@link IOrderedProvider#HIGHEST_SYSTEM_PRIORITY} so that Mercator runs before all
     * other mod locators and its jars are visible to JarInJar resolution.
     *
     * @return the locator priority
     */
    @Override
    public int getPriority() {
        return IOrderedProvider.HIGHEST_SYSTEM_PRIORITY;
    }
}
