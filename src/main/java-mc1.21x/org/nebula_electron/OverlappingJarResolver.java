package org.nebula_electron;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.*;

/**
 * Fixes duplicate mod-ID crashes that happen when a jar bundles itself via JarInJar.
 *
 * <p>Some mods ship an outer wrapper jar that embeds a JarInJar inner jar, both declaring the
 * same {@code modId}. NeoForge's {@code JarInJarDependencyLocator} then extracts and loads the
 * inner jar alongside the outer one, which causes a duplicate-mod error at startup.
 *
 * <p>When that pattern is detected, this class extracts the inner jar to the JIJ cache via
 * {@link JijCache} and returns a filtered overlay using {@link JarContents#ofFilteredPaths}
 * that strips the outer jar's {@code META-INF/jarjar/} metadata so the locator never sees the
 * duplicate. Jars that don't have JarJar metadata, or whose inner jar has a different mod ID,
 * come back unchanged via {@link JarContents#ofPath}.
 */
public class OverlappingJarResolver {

    private static final JarContents.PathFilter OUTER_FILTER = p ->
            !p.startsWith("META-INF/jarjar/")
            && !p.equals("META-INF/neoforge.mods.toml")
            && !p.equals("META-INF/MANIFEST.MF");

    private final JijCache jijCache = new JijCache(FMLPaths.JIJ_CACHEDIR.get());

    /**
     * Returns the {@link JarContents} to load for the given jar.
     *
     * <p>If no conflict is detected the jar is returned via {@link JarContents#ofPath}.
     * If the inner jar shares the outer mod ID, the inner jar is extracted to the JIJ cache
     * and a filtered overlay is returned that hides the JarJar metadata.
     *
     * @param jarPath    path to the outer mod jar
     * @param coordLabel Maven coordinate, used only in log output
     * @return the resolved {@link JarContents}
     * @throws IOException if reading the jar or writing to the cache fails
     */
    public Object resolve(Path jarPath, String coordLabel) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            if (zip.getEntry("META-INF/jarjar/metadata.json") == null)
                return JarContents.ofPath(jarPath);

            String outerModId = readModId(zip);
            if (outerModId == null)
                return JarContents.ofPath(jarPath);

            String metadata;
            try (InputStream in = zip.getInputStream(zip.getEntry("META-INF/jarjar/metadata.json"))) {
                metadata = new String(in.readAllBytes());
            }

            String innerEntryPath = findInnerJarWithSameModId(zip, metadata, outerModId);
            if (innerEntryPath == null)
                return JarContents.ofPath(jarPath);

            String filename = innerEntryPath.substring(innerEntryPath.lastIndexOf('/') + 1);
            Path innerJar = jijCache.extract(zip, innerEntryPath, filename);

            System.out.println("[Mercator] Using JarContents overlay for: " + coordLabel);
            return JarContents.ofFilteredPaths(List.of(
                    new JarContents.FilteredPath(innerJar),
                    new JarContents.FilteredPath(jarPath, OUTER_FILTER)));
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
