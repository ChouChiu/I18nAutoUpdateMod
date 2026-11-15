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

    public static void seedCache(Path source, Path cacheFile) throws IOException {
        if (Files.notExists(cacheFile) && Files.isRegularFile(source)) {
            writeAtomically(cacheFile, temporary ->
                    Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES));
            Log.info("Seeded cache: %s -> %s", source, cacheFile);
        }
    }

    @FunctionalInterface
    public interface FileWriter {
        void write(Path temporary) throws IOException;
    }

    public static void writeAtomically(Path target, FileWriter writer) throws IOException {
        Path absoluteTarget = target.toAbsolutePath();
        Files.createDirectories(absoluteTarget.getParent());
        Path temporary = Files.createTempFile(absoluteTarget.getParent(), ".i18n-", ".part");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    public static void migrateDirectory(Path source, Path target) throws IOException {
        if (source.equals(target) || !Files.isDirectory(source)) {
            return;
        }

        Files.createDirectories(target.getParent());
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

        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path destination = target.resolve(source.relativize(file));
                if (attrs.isRegularFile() && Files.notExists(destination)) {
                    seedCache(file, destination);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Log.info("Merged legacy storage without overwriting: %s -> %s", source, target);
    }
}
