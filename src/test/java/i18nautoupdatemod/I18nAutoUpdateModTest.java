package i18nautoupdatemod;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nAutoUpdateModTest {
    @AfterEach
    void resetUpdater() {
        I18nAutoUpdateMod.resetUpdateForTests();
    }

    @Test
    void startsOnceAndReturnsWithoutWaitingForWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blockingUpdate = () -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        long startNanos = System.nanoTime();
        CompletableFuture<Void> first = I18nAutoUpdateMod.startAsyncOnce(blockingUpdate);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        CompletableFuture<Void> second = I18nAutoUpdateMod.startAsyncOnce(() -> {
            throw new AssertionError("duplicate update");
        });

        assertTrue(elapsedMillis < 500, "startAsyncOnce blocked for " + elapsedMillis + " ms");
        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertSame(first, second);
        release.countDown();
        first.get(2, TimeUnit.SECONDS);
    }

    @Test
    void usesModernResourcePackIdForCalendarVersioning() {
        String fileName = "Minecraft-Mod-Language-Modpack-Converted-26.1.2.zip";
        assertEquals(
                "file/" + fileName,
                I18nAutoUpdateMod.resourcePackId("26.1.2", fileName));
        assertEquals(
                "file/" + fileName,
                I18nAutoUpdateMod.resourcePackId("26.3", fileName));
        assertEquals(
                "file/" + fileName,
                I18nAutoUpdateMod.resourcePackId("1.13", fileName));
        assertEquals(
                fileName,
                I18nAutoUpdateMod.resourcePackId("1.12.2", fileName));
    }

    @Test
    void readsInitialDownloadTimeoutProperty() {
        String old = System.getProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
        try {
            System.clearProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
            assertEquals(10, I18nAutoUpdateMod.getInitialDownloadTimeoutSeconds());

            System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, "5");
            assertEquals(5, I18nAutoUpdateMod.getInitialDownloadTimeoutSeconds());

            System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, "0");
            assertEquals(0, I18nAutoUpdateMod.getInitialDownloadTimeoutSeconds());

            System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, "invalid");
            assertEquals(10, I18nAutoUpdateMod.getInitialDownloadTimeoutSeconds());
        } finally {
            if (old != null) {
                System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, old);
            } else {
                System.clearProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
            }
        }
    }

    @Test
    void initDoesNotWaitWhenPackAlreadyExists(@TempDir Path tempDir) throws Exception {
        Path resourcepacks = tempDir.resolve("resourcepacks");
        Files.createDirectories(resourcepacks);
        String packFileName = "Minecraft-Mod-Language-Modpack-Converted-26.3.zip";
        Files.createFile(resourcepacks.resolve(packFileName));

        CountDownLatch latch = new CountDownLatch(1);
        I18nAutoUpdateMod.startAsyncOnce(() -> {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        });

        long start = System.currentTimeMillis();
        I18nAutoUpdateMod.init(tempDir, "26.3", "Fabric", new HashSet<>());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 500, "init should not wait when pack exists, took " + elapsed + " ms");
        latch.countDown();

        Path options = tempDir.resolve("options.txt");
        assertTrue(Files.exists(options));
        String optionsContent = new String(Files.readAllBytes(options));
        assertTrue(optionsContent.contains(packFileName));
    }

    @Test
    void initWaitsAndRegistersWhenPackIsMissingAndCompletedInTime(@TempDir Path tempDir) throws Exception {
        Path resourcepacks = tempDir.resolve("resourcepacks");
        String packFileName = "Minecraft-Mod-Language-Modpack-Converted-26.3.zip";
        Path packPath = resourcepacks.resolve(packFileName);

        I18nAutoUpdateMod.startAsyncOnce(() -> {
            try {
                Thread.sleep(100);
                Files.createDirectories(resourcepacks);
                Files.createFile(packPath);
            } catch (Exception ignored) {
            }
        });

        I18nAutoUpdateMod.init(tempDir, "26.3", "Fabric", new HashSet<>());

        assertTrue(Files.exists(packPath));
        Path options = tempDir.resolve("options.txt");
        assertTrue(Files.exists(options));
        String optionsContent = new String(Files.readAllBytes(options));
        assertTrue(optionsContent.contains(packFileName));
    }

    @Test
    void initDoesNotBlockWhenTimeoutIsZero(@TempDir Path tempDir) throws Exception {
        String old = System.getProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, "0");
            I18nAutoUpdateMod.startAsyncOnce(() -> {
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });

            long start = System.currentTimeMillis();
            I18nAutoUpdateMod.init(tempDir, "26.3", "Fabric", new HashSet<>());
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 500, "init with timeout=0 took " + elapsed + " ms");
        } finally {
            latch.countDown();
            if (old != null) {
                System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, old);
            } else {
                System.clearProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
            }
        }
    }

    @Test
    void initHandlesTimeoutGracefullyWithoutException(@TempDir Path tempDir) throws Exception {
        String old = System.getProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
        CountDownLatch latch = new CountDownLatch(1);
        try {
            System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, "1");
            I18nAutoUpdateMod.startAsyncOnce(() -> {
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });

            long start = System.currentTimeMillis();
            I18nAutoUpdateMod.init(tempDir, "26.3", "Fabric", new HashSet<>());
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed >= 900 && elapsed < 3000, "init should wait ~1s on timeout, took " + elapsed + " ms");
        } finally {
            latch.countDown();
            if (old != null) {
                System.setProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY, old);
            } else {
                System.clearProperty(I18nAutoUpdateMod.INITIAL_TIMEOUT_PROPERTY);
            }
        }
    }
}
