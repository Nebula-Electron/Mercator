package org.nebula_electron;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Mod locator for NeoForge on Minecraft 1.20.x, reads jars from a local Maven repo.
 *
 * <p>Uses {@link MavenModLocator} to get the jar list and {@link OverlappingJarResolver}
 * to handle JarInJar conflicts before adding jars to the pipeline. Compiled against
 * {@code securejarhandler 3.x} where {@link cpw.mods.jarhandling.JarContents#of} is used
 * instead of the 1.21.x {@code ofPath}/{@code ofFilteredPaths}.
 */
public class MavenModLocatorRegistrar implements IModFileCandidateLocator {

    /**
     * Resolves mod jars from the list file and adds each one to the discovery pipeline.
     */
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        MavenModLocator locator = new MavenModLocator();
        OverlappingJarResolver resolver = new OverlappingJarResolver();

        System.out.println("[Mercator] Running on the 1.20x branch");

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
