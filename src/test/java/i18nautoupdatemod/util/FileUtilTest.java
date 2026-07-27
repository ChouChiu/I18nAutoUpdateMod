package i18nautoupdatemod.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void movesLegacyDirectoryWhenNewDirectoryDoesNotExist() throws Exception {
        Path source = temporaryDirectory.resolve(".i18nupdatemod");
        Path target = temporaryDirectory.resolve(".i18nautoupdatemod");
        Files.createDirectories(source.resolve("1.21"));
        Files.write(source.resolve("1.21/pack.zip"), "old".getBytes(StandardCharsets.UTF_8));

        FileUtil.migrateDirectory(source, target);

        assertTrue(Files.exists(target.resolve("1.21/pack.zip")));
        assertFalse(Files.exists(source));
    }

    @Test
    void mergesOnlyMissingFilesWhenBothDirectoriesExist() throws Exception {
        Path source = temporaryDirectory.resolve("old");
        Path target = temporaryDirectory.resolve("new");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.write(source.resolve("same.txt"), "legacy".getBytes(StandardCharsets.UTF_8));
        Files.write(source.resolve("missing.txt"), "copied".getBytes(StandardCharsets.UTF_8));
        Files.write(target.resolve("same.txt"), "current".getBytes(StandardCharsets.UTF_8));

        FileUtil.migrateDirectory(source, target);

        assertEquals("current", new String(
                Files.readAllBytes(target.resolve("same.txt")), StandardCharsets.UTF_8));
        assertEquals("copied", new String(
                Files.readAllBytes(target.resolve("missing.txt")), StandardCharsets.UTF_8));
    }
}
