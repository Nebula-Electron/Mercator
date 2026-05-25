package org.nebula_electron;

import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.zip.*;

/**
 * Content-addressed cache for JarInJar (JIJ) inner jars.
 *
 * <p>Inner jars are extracted from their containing zip to a directory managed by FML
 * ({@code FMLPaths.JIJ_CACHEDIR}), keyed by their SHA-256 checksum. Re-extracting the same bytes
 * is a no-op: if the destination already exists the temporary file is simply deleted.
 */
public class JijCache {

    /**
     * Extracts a zip entry to the JIJ cache directory, keyed by its SHA-256 hash.
     *
     * <p>The entry is first written to a temp file in the cache directory. After hashing, it is
     * moved atomically to {@code <cacheDir>/<sha256>/<filename>}. If the destination already
     * exists (a previously cached extraction), the temp file is discarded and the existing path is
     * returned.
     *
     * @param zip       the zip file containing the entry to extract
     * @param entryPath the path of the entry within {@code zip}
     * @param filename  the file name to use for the cached artifact
     * @return the path of the cached jar file
     * @throws IOException if reading, hashing, or moving the file fails
     */
    public Path extract(ZipFile zip, String entryPath, String filename) throws IOException {
        Path cacheDir = FMLPaths.JIJ_CACHEDIR.get();
        Path tmp = Files.createTempFile(cacheDir, "_jij", ".tmp");
        String checksum;
        try {
            MessageDigest digest = newSha256();
            try (InputStream in = zip.getInputStream(zip.getEntry(entryPath));
                DigestOutputStream out = new DigestOutputStream(Files.newOutputStream(tmp), digest)) {
                in.transferTo(out);
            }
            checksum = HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        Path dest = cacheDir.resolve(checksum + "/" + filename);
        if (!Files.isRegularFile(dest)) {
            Files.createDirectories(dest.getParent());
            try {
                Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Files.deleteIfExists(tmp);
        }
        return dest;
    }

    /**
     * Creates a SHA-256 {@link MessageDigest} instance.
     *
     * @return a fresh {@code SHA-256} digest
     * @throws RuntimeException if the JVM does not support SHA-256 (never happens on compliant JVMs)
     */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
