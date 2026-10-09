package i18nautoupdatemod.util;

import com.sun.net.httpserver.HttpServer;
import i18nautoupdatemod.core.ModConfig;
import i18nautoupdatemod.entity.AssetSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetUtilTest {
    private HttpServer fastServer;
    private HttpServer slowServer;

    @AfterEach
    void stopServers() {
        if (fastServer != null) {
            fastServer.stop(0);
        }
        if (slowServer != null) {
            slowServer.stop(0);
        }
    }

    @Test
    void mainlandOrderingProbesActualChecksumAndKeepsGithubFallback() throws Exception {
        fastServer = checksumServer(0);
        slowServer = checksumServer(150);
        List<AssetUtil.SourceRoot> roots = Arrays.asList(
                new AssetUtil.SourceRoot("GitHub", "http://127.0.0.1:1/", false),
                new AssetUtil.SourceRoot("Slow", baseUrl(slowServer), true),
                new AssetUtil.SourceRoot("Fast", baseUrl(fastServer), true)
        );

        List<AssetSource> ordered = AssetUtil.orderSources(
                "pack.zip", "pack.md5", true, roots);

        assertEquals("Fast", ordered.get(0).name);
        assertEquals("Slow", ordered.get(1).name);
        assertEquals("GitHub", ordered.get(2).name);
    }

    @Test
    void overseasOrderingAlwaysPrefersGithub() {
        List<AssetUtil.SourceRoot> roots = Arrays.asList(
                new AssetUtil.SourceRoot("GitHub", "https://example.invalid/github/", false),
                new AssetUtil.SourceRoot("CFPA", "https://example.invalid/cfpa/", true)
        );
        List<AssetSource> ordered = AssetUtil.orderSources(
                "pack.zip", "pack.md5", false, roots);
        assertEquals("GitHub", ordered.get(0).name);
        assertEquals("CFPA", ordered.get(1).name);
    }

    @Test
    void configuredDefaultSourceSkipsDetectionAndFollowsPriority() {
        List<AssetUtil.SourceRoot> roots = Arrays.asList(
                new AssetUtil.SourceRoot("GitHub", "https://example.invalid/github/", false),
                new AssetUtil.SourceRoot("CFPA", "https://example.invalid/cfpa/", true),
                new AssetUtil.SourceRoot("Community Mirror", "https://example.invalid/community/", true)
        );
        List<AssetSource> ordered = AssetUtil.orderSources(
                "pack.zip", "pack.md5", "Community Mirror",
                Arrays.asList("GitHub", "CFPA", "Community Mirror"),
                () -> {
                    throw new AssertionError("location detection must be skipped");
                },
                roots);
        assertEquals("Community Mirror", ordered.get(0).name);
        assertEquals("GitHub", ordered.get(1).name);
        assertEquals("CFPA", ordered.get(2).name);
    }

    @Test
    void autoOrderingUsesPriorityAfterPreferredSource() throws Exception {
        fastServer = checksumServer(0);
        List<AssetUtil.SourceRoot> roots = Arrays.asList(
                new AssetUtil.SourceRoot("GitHub", "https://example.invalid/github/", false),
                new AssetUtil.SourceRoot("Down", "http://127.0.0.1:1/", true),
                new AssetUtil.SourceRoot("Fast", baseUrl(fastServer), true)
        );
        List<String> priority = Arrays.asList("Down", "Fast", "GitHub");

        List<AssetSource> mainland = AssetUtil.orderSources(
                "pack.zip", "pack.md5", null, priority, () -> true, roots);
        assertEquals("Fast", mainland.get(0).name);
        assertEquals("GitHub", mainland.get(1).name);
        assertEquals("Down", mainland.get(2).name);

        List<AssetSource> overseas = AssetUtil.orderSources(
                "pack.zip", "pack.md5", null, priority, () -> false, roots);
        assertEquals("GitHub", overseas.get(0).name);
        assertEquals("Down", overseas.get(1).name);
        assertEquals("Fast", overseas.get(2).name);
    }

    @Test
    void betaPackPutsAutoMergeFirstAndKeepsStableFallback() {
        ModConfig config = ModConfig.defaults();
        config.defaultSource = "GitHub";
        List<AssetSource> stable = AssetUtil.resolveSources("pack.zip", "pack.md5", config);
        assertTrue(stable.stream().noneMatch(source -> source.name.equals("AutoMerge")));

        config.betaPack = true;
        List<AssetSource> beta = AssetUtil.resolveSources("pack.zip", "pack.md5", config);
        assertEquals(stable.size() + 1, beta.size());
        assertEquals("AutoMerge", beta.get(0).name);
        assertEquals(AssetUtil.AUTOMERGE_ASSET_ROOT + "pack.zip", beta.get(0).fileUrl);
        assertEquals(AssetUtil.AUTOMERGE_ASSET_ROOT + "pack.md5", beta.get(0).checksumUrl);
        assertEquals("GitHub", beta.get(1).name);
    }

    @Test
    void readTimeoutDoesNotHangSourceSelection() throws Exception {
        slowServer = checksumServer(500);
        assertThrows(IOException.class, () -> AssetUtil.getString(
                baseUrl(slowServer) + "pack.md5", 100, 100));
    }

    private static HttpServer checksumServer(long delayMillis) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack.md5", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] response = "d41d8cd98f00b204e9800998ecf8427e".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }
}
