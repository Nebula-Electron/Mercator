package org.nebula_electron;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Path;
import java.util.zip.*;

/**
 * Same conflict resolution as {@link OverlappingJarResolver} in this source set, but named
 * separately so {@link MavenModLocatorRegistrar20x} can reference it explicitly when called
 * from the version-dispatching registrar in {@code java-mc-dispatcher}.
 *
 * @see OverlappingJarResolver the 1.21.x version that can use a filtered overlay
 */
public class OverlappingJarResolver20x {

    private final JijCache jijCache = new JijCache(FMLPaths.GAMEDIR.get().resolve("jij-cache"));

    /**
     * Returns the {@link JarContents} to load for the given jar.
     *
     * <p>Returns the outer jar as-is unless it has JarJar metadata and an inner jar with the
     * same mod ID, in which case the inner jar is extracted to the cache and returned directly.
     *
     * @param jarPath    path to the outer mod jar
     * @param coordLabel Maven coordinate, used only in log output
     * @return the resolved {@link JarContents}
     * @throws IOException if reading the jar or writing to the cache fails
     */
    public Object resolve(Path jarPath, String coordLabel) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            if (zip.getEntry("META-INF/jarjar/metadata.json") == null)
                return JarContents.of(jarPath);

            String outerModId = readModId(zip);
            if (outerModId == null)
                return JarContents.of(jarPath);

            String metadata;
            try (InputStream in = zip.getInputStream(zip.getEntry("META-INF/jarjar/metadata.json"))) {
                metadata = new String(in.readAllBytes());
            }

            String innerEntryPath = findInnerJarWithSameModId(zip, metadata, outerModId);
            if (innerEntryPath == null)
                return JarContents.of(jarPath);

            String filename = innerEntryPath.substring(innerEntryPath.lastIndexOf('/') + 1);
            Path innerJar = jijCache.extract(zip, innerEntryPath, filename);

            System.out.println("[Mercator] Using inner jar for: " + coordLabel);
            // securejarhandler 3.x has no path filter, use the inner jar directly to avoid
            // duplicate-modId conflicts caused by the outer jar's jarjar metadata.
            return JarContents.of(innerJar);
        }
    }

    /**
     * Walks the JarJar metadata JSON looking for an inner jar whose {@code neoforge.mods.toml}
     * has the same mod ID as the outer jar.
     *
     * @param zip         the outer zip to read inner jar entries from
     * @param metadata    raw contents of {@code META-INF/jarjar/metadata.json}
     * @param targetModId the mod ID to look for
     * @return the zip-entry path of the matching inner jar, or null if not found
     * @throws IOException if reading an inner jar entry fails
     */
    private String findInnerJarWithSameModId(ZipFile zip, String metadata, String targetModId) throws IOException {
        int pos = 0;
        while (true) {
            int idx = metadata.indexOf("\"path\"", pos);
            if (idx == -1) break;
            int colon = metadata.indexOf(':', idx + 6);
            int open  = metadata.indexOf('"', colon + 1);
            int close = metadata.indexOf('"', open + 1);
            if (colon == -1 || open == -1 || close == -1) break;
            String innerPath = metadata.substring(open + 1, close);
            pos = close + 1;

            ZipEntry entry = zip.getEntry(innerPath);
            if (entry == null) continue;
            try (ZipInputStream zis = new ZipInputStream(zip.getInputStream(entry))) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if ("META-INF/neoforge.mods.toml".equals(e.getName())) {
                        if (targetModId.equals(extractModId(new String(zis.readAllBytes()))))
                            return innerPath;
                        break;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Reads the mod ID out of {@code neoforge.mods.toml} in the zip.
     *
     * @param zip the zip to read from
     * @return the mod ID, or null if the entry is missing or the field isn't found
     * @throws IOException if reading fails
     */
    private String readModId(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/neoforge.mods.toml");
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return extractModId(new String(in.readAllBytes()));
        }
    }

    /**
     * Pulls the first {@code modId} value out of a TOML string with basic string scanning.
     *
     * @param toml raw TOML content
     * @return the mod ID value, or null if the key isn't present
     */
    private String extractModId(String toml) {
        int idx = toml.indexOf("modId");
        if (idx == -1) return null;
        int eq    = toml.indexOf('=', idx + 5);
        int open  = toml.indexOf('"', eq + 1);
        int close = toml.indexOf('"', open + 1);
        if (eq == -1 || open == -1 || close == -1) return null;
        return toml.substring(open + 1, close);
    }
}
