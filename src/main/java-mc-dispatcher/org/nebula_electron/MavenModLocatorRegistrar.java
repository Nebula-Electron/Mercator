package org.nebula_electron;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.*;

/**
 * Single service entry point that picks the right registrar at runtime based on
 * the running Minecraft version.
 *
 * <p>Checks the MC version via {@link MavenModLocator#getMinecraftVersion()} and hands off to
 * {@link MavenModLocatorRegistrar21x} for 1.21.9+ or {@link MavenModLocatorRegistrar20x}
 * for older versions. This avoids needing separate service files per NeoForge version.
 */
public class MavenModLocatorRegistrar implements IModFileCandidateLocator {

    /**
     * Picks the right branch registrar based on the detected MC version and runs it.
     */
    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        MavenModLocator locator = new MavenModLocator();
        String mcVersion = locator.getMinecraftVersion();

        IModFileCandidateLocator impl;
        if (isAtLeast(mcVersion, "1.21.9")) {
            System.out.println("[Mercator] Delegating to 1.21x branch for MC " + mcVersion);
            impl = new MavenModLocatorRegistrar21x();
        } else {
            System.out.println("[Mercator] Delegating to 1.20x branch for MC " + mcVersion);
            impl = new MavenModLocatorRegistrar20x();
        }
        impl.findCandidates(context, pipeline);
    }

    /** Runs before all other mod locators. */
    @Override
    public int getPriority() {
        return IOrderedProvider.HIGHEST_SYSTEM_PRIORITY;
    }

    /**
     * Returns true if {@code version} is at least {@code atLeast}, comparing the first
     * three numeric components. Missing components count as 0.
     *
     * @param version  version to test, e.g. {@code "1.21.4"}
     * @param atLeast  minimum version, e.g. {@code "1.21.9"}
     * @return true if version >= atLeast
     */
    static boolean isAtLeast(String version, String atLeast) {
        int[] v = parse(version);
        int[] a = parse(atLeast);
        for (int i = 0; i < 3; i++) {
            if (v[i] != a[i]) return v[i] > a[i];
        }
        return true;
    }

    private static int[] parse(String ver) {
        String[] parts = ver.split("\\.");
        return new int[]{
                parts.length > 0 ? Integer.parseInt(parts[0]) : 0,
                parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                parts.length > 2 ? Integer.parseInt(parts[2]) : 0
        };
    }
}
