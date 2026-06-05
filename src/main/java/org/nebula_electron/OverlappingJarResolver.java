package org.nebula_electron;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Base stub for the overlapping-jar resolver, compiled without any version-specific API.
 *
 * <p>The real logic lives in the versioned source sets ({@code java-mc1.20x} or
 * {@code java-mc1.21x}). This stub only runs if neither versioned class is selected,
 * in which case it returns the jar path unchanged as a safe fallback.
 */
public class OverlappingJarResolver {

    /**
     * Returns the jar path as-is.
     *
     * @param jarPath    path to the mod jar
     * @param coordLabel Maven coordinate (unused in this stub)
     * @return {@code jarPath} unchanged
     */
    public Object resolve(Path jarPath, String coordLabel) throws IOException {
        return jarPath;
    }
}
