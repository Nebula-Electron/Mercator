package org.nebula_electron;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Forge (not NeoForge) mod locator that pulls jars from a local Maven repository.
 *
 * <p>Plugs into Forge's mod-discovery pipeline via {@link AbstractJarFileModLocator}.
 * All coordinate parsing and jar resolution is handled by {@link MavenModLocator}.
 */
public class MavenModLocatorRegistrar extends AbstractJarFileModLocator {

    /** Returns the jar paths resolved from the mod list file. */
    @Override
    public Stream<Path> scanCandidates() {
        MavenModLocator locator = new MavenModLocator();
        System.out.println("[Mercator] Delegating to 1.20x branch for MC " + locator.getMinecraftVersion());
        return locator.resolveModJars().stream();
    }

    /** @return {@code "mercator"} */
    @Override
    public String name() {
        return "mercator";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {}
}
