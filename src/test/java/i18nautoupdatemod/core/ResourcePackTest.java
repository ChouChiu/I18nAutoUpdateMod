package i18nautoupdatemod.core;

import com.sun.net.httpserver.HttpServer;
import i18nautoupdatemod.entity.AssetSource;
import i18nautoupdatemod.util.DigestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourcePackTest {
    @TempDir
    Path temporaryDirectory;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fallsBackAfterChecksumMismatch() throws Exception {
        byte[] correct = zipBytes("hello");
        byte[] checksum = checksum(correct);
        byte[] wrong = "wrong".getBytes(StandardCharsets.UTF_8);
        AtomicInteger firstDownloads = new AtomicInteger();
        AtomicInteger secondDownloads = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addResponse("/first/pack.md5", checksum);
        addResponse("/first/pack.zip", wrong, firstDownloads);
        addResponse("/second/pack.md5", checksum);
        addResponse("/second/pack.zip", correct, secondDownloads);
        server.start();

        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        ResourcePack pack = new ResourcePack(
                "pack.zip",
                temporaryDirectory.resolve("resourcepacks"),
                temporaryDirectory.resolve("cache"));
        pack.checkUpdate(() -> Arrays.asList(
                new AssetSource("first", root + "/first/pack.zip", root + "/first/pack.md5"),
                new AssetSource("second", root + "/second/pack.zip", root + "/second/pack.md5")
        ));

        assertArrayEquals(correct, Files.readAllBytes(pack.getCacheFile()));
        assertEquals(1, firstDownloads.get());
        assertEquals(1, secondDownloads.get());
    }

    @Test
    void keepsStaleCacheWhenAllSourcesFail() throws Exception {
        Path cacheDirectory = temporaryDirectory.resolve("cache");
        Files.createDirectories(cacheDirectory);
        Path cached = cacheDirectory.resolve("pack.zip");
        byte[] stale = zipBytes("stale");
        Files.write(cached, stale);
        Files.setLastModifiedTime(
                cached,
                FileTime.fromMillis(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        String root = "http://127.0.0.1:" + server.getAddress().getPort();

        ResourcePack pack = new ResourcePack(
                "pack.zip",
                temporaryDirectory.resolve("resourcepacks"),
                cacheDirectory);
        pack.checkUpdate(() -> Collections.singletonList(
                new AssetSource("missing", root + "/pack.zip", root + "/pack.md5")));

        assertArrayEquals(stale, Files.readAllBytes(cached));
    }

    @Test
    void github404FallsBackToCfpa() throws Exception {
        byte[] packBytes = zipBytes("legacy pack");
        AtomicInteger githubRequests = new AtomicInteger();
        AtomicInteger cfpaRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/github/pack.md5", exchange -> {
            githubRequests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        addResponse("/cfpa/pack.md5",
                checksum(packBytes));
        addResponse("/cfpa/pack.zip", packBytes, cfpaRequests);
        server.start();

        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        ResourcePack pack = new ResourcePack(
                "pack.zip",
                temporaryDirectory.resolve("resourcepacks"),
                temporaryDirectory.resolve("cache"));
        pack.checkUpdate(() -> Arrays.asList(
                new AssetSource(
                        "GitHub",
                        root + "/github/pack.zip",
                        root + "/github/pack.md5"),
                new AssetSource(
                        "CFPA",
                        root + "/cfpa/pack.zip",
                        root + "/cfpa/pack.md5")
        ));

        assertArrayEquals(packBytes, Files.readAllBytes(pack.getCacheFile()));
        assertEquals(1, githubRequests.get());
        assertEquals(1, cfpaRequests.get());
    }

    @Test
    void rechecksCorruptFreshCacheAndRejectsInvalidDownloadWithMatchingChecksum() throws Exception {
        byte[] invalid = "not a ZIP".getBytes(StandardCharsets.UTF_8);
        byte[] valid = zipBytes("valid");
        Path cache = temporaryDirectory.resolve("cache");
        Files.createDirectories(cache);
        Files.write(cache.resolve("pack.zip"), invalid);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addResponse("/invalid.md5", checksum(invalid));
        addResponse("/invalid.zip", invalid);
        addResponse("/valid.md5", checksum(valid));
        addResponse("/valid.zip", valid);
        server.start();

        ResourcePack pack = new ResourcePack("pack.zip", temporaryDirectory, cache);
        assertThrows(IOException.class, () -> pack.checkUpdate(
                () -> Collections.singletonList(source("invalid"))));
        pack.checkUpdate(() -> Arrays.asList(source("invalid"), source("valid")));
        assertArrayEquals(valid, Files.readAllBytes(pack.getCacheFile()));
    }

    @Test
    void unchangedChecksumRefreshesTtlWithoutDownloading() throws Exception {
        byte[] existing = zipBytes("existing");
        Path cache = Files.createDirectories(temporaryDirectory.resolve("cache"));
        Path file = Files.write(cache.resolve("pack.zip"), existing);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)));

        AtomicInteger checks = new AtomicInteger();
        AtomicInteger downloads = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addResponse("/same.md5", checksum(existing), checks);
        addResponse("/same.zip", existing, downloads);
        server.start();

        ResourcePack pack = new ResourcePack("pack.zip", temporaryDirectory, cache);
        pack.checkUpdate(() -> Collections.singletonList(source("same")));
        assertEquals(1, checks.get());
        assertEquals(0, downloads.get());
        pack.checkUpdate(() -> {
            throw new AssertionError("Cache within TTL should skip source resolution");
        });
    }

    private AssetSource source(String name) {
        String root = "http://127.0.0.1:" + server.getAddress().getPort() + "/" + name;
        return new AssetSource(name, root + ".zip", root + ".md5");
    }

    private byte[] checksum(byte[] bytes) throws IOException {
        Path file = Files.createTempFile(temporaryDirectory, "checksum", ".zip");
        Files.write(file, bytes);
        return DigestUtil.md5Hex(file).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] zipBytes(String description) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(("{\"pack\":{\"description\":\"" + description + "\",\"pack_format\":3}}")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private void addResponse(String path, byte[] response) {
        addResponse(path, response, new AtomicInteger());
    }

    private void addResponse(String path, byte[] response, AtomicInteger requests) {
        server.createContext(path, exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
    }
}
