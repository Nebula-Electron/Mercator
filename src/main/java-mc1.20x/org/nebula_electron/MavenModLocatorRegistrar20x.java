package org.nebula_electron;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Mod locator for NeoForge on Minecraft 1.20.x, called by the version-dispatching registrar.
 *
 * <p>The dispatcher in {@code java-mc-dispatcher} picks this class when the detected MC version
 * is below 1.21.9. Uses {@link OverlappingJarResolver20x} which targets the
 * {@code securejarhandler 3.x} API in MC 1.20.x.
 */
public class MavenModLocatorRegistrar20x implements IModFileCandidateLocator {

    /**
     * Resolves mod jars from the list file and adds each one to the discovery pipeline.
     */
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        MavenModLocator locator = new MavenModLocator();
        OverlappingJarResolver20x resolver = new OverlappingJarResolver20x();

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
