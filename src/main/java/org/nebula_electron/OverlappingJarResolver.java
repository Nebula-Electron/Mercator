package org.nebula_electron;

import net.neoforged.fml.jarcontents.JarContents;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Resolves mod jars that would cause a duplicate mod-ID conflict if loaded naively.
 *
 * <p>Some mods are published as an <em>outer</em> wrapper jar that contains a JarInJar (JIJ)
 * <em>inner</em> jar. Both declare the same {@code modId} in their {@code neoforge.mods.toml},
 * which makes NeoForge's {@code JarInJarDependencyLocator} extract and load the inner jar
 * alongside the outer one — causing a duplicate-mod error at startup.
 *
 * <p>This class detects that pattern by comparing mod IDs and, when a conflict is found,
 * resolves it by:
 * <ol>
 *   <li>Extracting the inner jar to the JIJ cache (via {@link JijCache}) so it can be opened
 *       as a standalone {@link JarContents}.</li>
 *   <li>Overlaying the outer jar on top via {@link JarContents#ofFilteredPaths}, stripping
 *       its {@code META-INF/jarjar/} metadata so {@code JarInJarDependencyLocator} never
 *       sees the duplicate entry.</li>
 * </ol>
 *
 * <p>Jars without JarJar metadata, or whose inner jar carries a different mod ID, are returned
 * unchanged via {@link JarContents#ofPath}.
 */
public class OverlappingJarResolver {

    private static final JarContents.PathFilter OUTER_FILTER = p ->
            !p.startsWith("META-INF/jarjar/")
            && !p.equals("META-INF/neoforge.mods.toml")
            && !p.equals("META-INF/MANIFEST.MF");

    private final JijCache jijCache = new JijCache();

    /**
     * Returns the {@link JarContents} to use for the given jar.
     *
     * <p>If the jar contains no JarJar metadata, or its inner jar does not share the outer mod ID,
     * the jar is returned as-is via {@link JarContents#ofPath}. Otherwise the inner jar is
     * extracted to the JIJ cache and the result is a filtered overlay that hides the JarJar
     * metadata from {@code JarInJarDependencyLocator}.
     *
     * @param jarPath    path to the outer mod jar
     * @param coordLabel the Maven coordinate string, used only for console logging
     * @return the resolved {@link JarContents}
     * @throws IOException if reading the jar or extracting the inner jar fails
     */
    public JarContents resolve(java.nio.file.Path jarPath, String coordLabel) throws IOException {
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
            java.nio.file.Path innerJar = jijCache.extract(zip, innerEntryPath, filename);

            System.out.println("[Mercator] Using JarContents overlay for: " + coordLabel);
            return JarContents.ofFilteredPaths(List.of(
                    new JarContents.FilteredPath(innerJar),
                    new JarContents.FilteredPath(jarPath, OUTER_FILTER)));
        }
    }

    /**
     * Scans the JarJar metadata JSON for an inner jar whose {@code neoforge.mods.toml} declares
     * the same mod ID as the outer jar.
     *
     * @param zip         the outer zip file (used to read each candidate inner jar)
     * @param metadata    the raw contents of {@code META-INF/jarjar/metadata.json}
     * @param targetModId the mod ID to match against
     * @return the zip-entry path of the matching inner jar, or {@code null} if none found
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
     * Reads the mod ID from a {@code neoforge.mods.toml} entry inside a zip file.
     *
     * @param zip the zip file to read from
     * @return the mod ID string, or {@code null} if the entry is absent or the field is missing
     * @throws IOException if reading the entry fails
     */
    private String readModId(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/neoforge.mods.toml");
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return extractModId(new String(in.readAllBytes()));
        }
    }

    /**
     * Extracts the value of the first {@code modId} key from a TOML string using simple string
     * scanning (no full TOML parser).
     *
     * @param toml the raw TOML content as a string
     * @return the mod ID value, or {@code null} if the key or its quoted value cannot be located
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
