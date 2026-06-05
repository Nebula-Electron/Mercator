package org.nebula_electron;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Mod locator for NeoForge on Minecraft 1.21.9+, reads jars from a local Maven repo.
 *
 * <p>Uses {@link MavenModLocator} to get the jar list and {@link OverlappingJarResolver21x}
 * to handle any JarInJar mod-ID conflicts before adding jars to the pipeline.
 *
 * <p>Lives in {@code META-INF/versions/21/} and takes over from the base stub on Java 21+.
 */
public class MavenModLocatorRegistrar21x implements IModFileCandidateLocator {

    /**
     * Resolves mod jars from the list file and adds each one to the discovery pipeline.
     */
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        MavenModLocator locator = new MavenModLocator();
        OverlappingJarResolver21x resolver = new OverlappingJarResolver21x();

        System.out.println("[Mercator] Running on the 1.21x-26.x branch");

        List<Path> jars = locator.resolveModJars();

        for (Path jarPath : jars) {
            try {
                Object result = resolver.resolve(jarPath, jarPath.getFileName().toString());
                if (result instanceof JarContents contents) {
                    pipeline.addJarContent(contents,
                            ModFileDiscoveryAttributes.DEFAULT.withLocator(this),
                            IncompatibleFileReporting.ERROR);
                    System.out.println("[Mercator] Added: " + jarPath.getFileName());
                }
            } catch (IOException e) {
                System.err.println("[Mercator] Failed to register: " + jarPath);
                e.printStackTrace();
            }
        }

        System.out.println("[Mercator] Done");
    }

    /** Runs before all other mod locators so our jars are visible to JarInJar. */
    @Override
    public int getPriority() {
        return IOrderedProvider.HIGHEST_SYSTEM_PRIORITY;
    }
}
