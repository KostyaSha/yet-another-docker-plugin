package com.github.kostyasha.it.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.security.SecureRandom;

/**
 * @author Kanstantsin Shautsou
 */
public class TempFileHelper {
    private static final SecureRandom random = new SecureRandom();

    /**
     * Creates a temporary directory in the given directory, or in in the
     * temporary directory if dir is {@code null}.
     */
    public static File createTempDirectory(String prefix, Path dir) throws IOException {
        if (prefix == null) {
            prefix = "";
        }
        final File file = generatePath(prefix, dir).toFile();
        if (!file.mkdirs()) {
            throw new IOException("Can't create dir " + file.getAbsolutePath());
        }
        return file;
    }

    private static Path generatePath(String prefix, Path dir) {
        long n = random.nextLong();
        n = (n == Long.MIN_VALUE) ? 0 : Math.abs(n);
        Path name = dir.getFileSystem().getPath(prefix + Long.toString(n));
        // the generated name should be a simple file name
        if (name.getParent() != null) {
            throw new IllegalArgumentException("Invalid prefix or suffix");
        }
        return dir.resolve(name);
    }
}
