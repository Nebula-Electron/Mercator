package org.nebula_electron;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.List;

public class MavenModLocator implements IModFileCandidateLocator {

    private final Path repoRoot;

    public MavenModLocator() {
        String repo = System.getProperty("maven.repo.root");

        this.repoRoot = (repo != null && !repo.isBlank())
                ? Paths.get(repo).toAbsolutePath().normalize()
                : Paths.get("../../common/modstore").toAbsolutePath().normalize();
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {

        System.out.println("[MavenModLocator] repoRoot=" + repoRoot);

        String modListPath = getJvmArg("maven.mod.list");

        System.out.println("[MavenModLocator] modListFile=" + modListPath);

        Path modList = (modListPath != null && !modListPath.isBlank())
                ? Paths.get(modListPath).toAbsolutePath().normalize()
                : null;

        System.out.println("[MavenModLocator] exists=" +
                (modList != null && Files.exists(modList)));

        if (modList == null || !Files.isRegularFile(modList)) {
            System.out.println("[MavenModLocator] No mod list found");
            return;
        }

        System.out.println("[MavenModLocator] Online");

        try {
            List<String> coordinates = Files.readAllLines(modList);

            for (String coord : coordinates) {

                coord = coord.trim();

                if (coord.isEmpty() || coord.startsWith("#")) {
                    continue;
                }

                Path jarPath = resolveCoordinate(coord);

                if (jarPath == null) {
                    System.out.println("[MavenModLocator] Invalid coordinate: " + coord);
                    continue;
                }

                if (!Files.isRegularFile(jarPath)) {
                    System.out.println("[MavenModLocator] Missing jar: " + jarPath);
                    continue;
                }

                try {

                    pipeline.addPath(
                            jarPath,
                            ModFileDiscoveryAttributes.DEFAULT.withLocator(this),
                            IncompatibleFileReporting.ERROR
                    );

                    System.out.println("[MavenModLocator] Added: " + coord);
                    System.out.println("[MavenModLocator] -> " + jarPath);

                } catch (Exception e) {

                    System.err.println("[MavenModLocator] Failed to register: " + jarPath);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed reading mod list", e);
        }
    }

    /**
     * Reads JVM arguments safely even when NeoForge filters system properties.
     */
    private String getJvmArg(String key) {

        return ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .stream()
                .filter(arg -> arg.startsWith("-D" + key + "="))
                .map(arg -> arg.substring(arg.indexOf('=') + 1))
                .findFirst()
                .orElse(null);
    }

    private Path resolveCoordinate(String coord) {

        String[] parts = coord.split(":");

        if (parts.length < 3) {
            return null;
        }

        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];

        Path resolved = repoRoot
                .resolve(group)
                .resolve(artifact)
                .resolve(version)
                .resolve(artifact + "-" + version + ".jar")
                .normalize();

        return resolved;
    }

    @Override
    public int getPriority() {
        return 100000;
    }
}