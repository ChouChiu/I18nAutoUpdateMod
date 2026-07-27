package i18nautoupdatemod.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileUtil {
    private FileUtil() {
    }

    public static void ensureDirectory(Path path) throws IOException {
        Files.createDirectories(path);
    }

    public static void seedCache(Path source, Path cacheFile) throws IOException {
        if (!Files.exists(cacheFile) && Files.isRegularFile(source)) {
            ensureDirectory(cacheFile.getParent());
            Files.copy(source, cacheFile, StandardCopyOption.COPY_ATTRIBUTES);
            Log.info("Seeded cache: %s -> %s", source, cacheFile);
        }
    }

    public static void atomicReplace(Path source, Path target) throws IOException {
        ensureDirectory(target.getParent());
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void copyAtomically(Path source, Path target) throws IOException {
        ensureDirectory(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            atomicReplace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void migrateDirectory(Path source, Path target) throws IOException {
        if (source.equals(target) || !Files.isDirectory(source)) {
            return;
        }

        ensureDirectory(target.getParent());
        if (!Files.exists(target)) {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                Log.info("Migrated storage: %s -> %s", source, target);
                return;
            } catch (AtomicMoveNotSupportedException ignored) {
                // Copying below also covers cross-filesystem migrations.
            } catch (IOException e) {
                Log.debug("Direct storage migration failed, merging instead: %s", e);
            }
        }

        ensureDirectory(target);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                ensureDirectory(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path destination = target.resolve(source.relativize(file));
                if (!Files.exists(destination)) {
                    Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Log.info("Merged legacy storage without overwriting: %s -> %s", source, target);
    }
}
