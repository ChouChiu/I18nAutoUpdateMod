package i18nautoupdatemod.core;

import com.sun.net.httpserver.HttpServer;
import i18nautoupdatemod.entity.AssetSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        byte[] correct = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] wrong = "wrong".getBytes(StandardCharsets.UTF_8);
        AtomicInteger firstDownloads = new AtomicInteger();
        AtomicInteger secondDownloads = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addResponse("/first/pack.md5", "5d41402abc4b2a76b9719d911017c592".getBytes(StandardCharsets.UTF_8));
        addResponse("/first/pack.zip", wrong, firstDownloads);
        addResponse("/second/pack.md5", "5d41402abc4b2a76b9719d911017c592".getBytes(StandardCharsets.UTF_8));
        addResponse("/second/pack.zip", correct, secondDownloads);
        server.start();

        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        ResourcePack pack = new ResourcePack(
                "pack.zip",
                temporaryDirectory.resolve("resourcepacks"),
                temporaryDirectory.resolve("cache"));
        pack.checkUpdate(Arrays.asList(
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
        byte[] stale = "stale".getBytes(StandardCharsets.UTF_8);
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
        pack.checkUpdate(Collections.singletonList(
                new AssetSource("missing", root + "/pack.zip", root + "/pack.md5")));

        assertArrayEquals(stale, Files.readAllBytes(cached));
    }

    @Test
    void github404FallsBackToCfpa() throws Exception {
        byte[] packBytes = "legacy pack".getBytes(StandardCharsets.UTF_8);
        AtomicInteger githubRequests = new AtomicInteger();
        AtomicInteger cfpaRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/github/pack.md5", exchange -> {
            githubRequests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        addResponse("/cfpa/pack.md5",
                "aa7a55e7399f1af2dfb3686c4a50dbc4".getBytes(StandardCharsets.UTF_8));
        addResponse("/cfpa/pack.zip", packBytes, cfpaRequests);
        server.start();

        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        ResourcePack pack = new ResourcePack(
                "pack.zip",
                temporaryDirectory.resolve("resourcepacks"),
                temporaryDirectory.resolve("cache"));
        pack.checkUpdate(Arrays.asList(
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
