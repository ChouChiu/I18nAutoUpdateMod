package i18nautoupdatemod.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetaResourcePackTest {
    private static final byte[] FORGE_PACK = "forge beta".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FABRIC_PACK = "fabric beta".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path cacheRoot;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesDirectoryIndex() {
        Map<String, BetaResourcePack.Entry> entries = BetaResourcePack.parseIndex(
                "<a href=\"./Minecraft-Mod-Language-Package-1.20-fabric-ABE200.zip\">x</a>"
                        + "<a href=\"./Minecraft-Mod-Language-Package-1.12.2-97701b.zip\">y</a>"
                        + "<a href=\"/\">root</a>");

        assertEquals(2, entries.size());
        assertEquals("ABE200", entries.get("1.20-fabric").hash);
        assertEquals("97701B", entries.get("1.12.2").hash);
        assertEquals("Minecraft-Mod-Language-Package-1.12.2-97701b.zip", entries.get("1.12.2").fileName);
    }

    @Test
    void downloadsMatchingVariantAndFallsBackToForge() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put(name("1.20", true, FABRIC_PACK), FABRIC_PACK);
        files.put(name("1.20", false, FORGE_PACK), FORGE_PACK);
        files.put(name("1.21", false, FORGE_PACK), FORGE_PACK);
        server = serve(files, true);

        BetaResourcePack beta = new BetaResourcePack(baseUrl(), cacheRoot);
        assertArrayEquals(FABRIC_PACK, Files.readAllBytes(beta.resolve("1.20", "Fabric")));
        assertArrayEquals(FORGE_PACK, Files.readAllBytes(beta.resolve("1.20", "Forge")));
        assertArrayEquals(FORGE_PACK, Files.readAllBytes(beta.resolve("1.21", "Quilt")));
        assertNull(beta.resolve("26.1", "Fabric"));
    }

    @Test
    void resolvesEveryVariantWhenMergingLoaders() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put(name("1.20", true, FABRIC_PACK), FABRIC_PACK);
        files.put(name("1.20", false, FORGE_PACK), FORGE_PACK);
        files.put(name("1.21", false, FORGE_PACK), FORGE_PACK);
        server = serve(files, true);

        BetaResourcePack beta = new BetaResourcePack(baseUrl(), cacheRoot);
        List<Path> forgeFirst = beta.resolveAll("1.20", "Forge");
        assertEquals(2, forgeFirst.size());
        assertArrayEquals(FORGE_PACK, Files.readAllBytes(forgeFirst.get(0)));
        assertArrayEquals(FABRIC_PACK, Files.readAllBytes(forgeFirst.get(1)));

        List<Path> fabricFirst = beta.resolveAll("1.20", "Fabric");
        assertArrayEquals(FABRIC_PACK, Files.readAllBytes(fabricFirst.get(0)));
        assertArrayEquals(FORGE_PACK, Files.readAllBytes(fabricFirst.get(1)));

        assertEquals(1, beta.resolveAll("1.21", "Fabric").size());
        assertTrue(beta.resolveAll("26.1", "Fabric").isEmpty());
    }

    @Test
    void rejectsChecksumMismatch() throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        files.put("Minecraft-Mod-Language-Package-1.20-000000.zip", FORGE_PACK);
        server = serve(files, true);

        assertNull(new BetaResourcePack(baseUrl(), cacheRoot).resolve("1.20", "Forge"));
        assertFalse(Files.exists(cacheRoot.resolve("1.20").resolve("forge")
                .resolve("Minecraft-Mod-Language-Package-1.20-000000.zip")));
    }

    @Test
    void usesCachedPackWhenIndexIsUnavailable() throws Exception {
        Path cached = cacheRoot.resolve("1.20").resolve("forge").resolve(name("1.20", false, FORGE_PACK));
        Files.createDirectories(cached.getParent());
        Files.write(cached, FORGE_PACK);
        server = serve(new HashMap<>(), false);

        Path resolved = new BetaResourcePack(baseUrl(), cacheRoot).resolve("1.20", "Forge");
        assertNotNull(resolved);
        assertTrue(Files.isSameFile(cached, resolved));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/project-hex/";
    }

    private static String name(String version, boolean fabric, byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        String hash = String.format("%02X%02X%02X", digest[0], digest[1], digest[2]);
        return "Minecraft-Mod-Language-Package-" + version + (fabric ? "-fabric" : "") + "-" + hash + ".zip";
    }

    private static HttpServer serve(Map<String, byte[]> files, boolean indexAvailable) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/project-hex/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath().substring("/project-hex/".length());
                byte[] response;
                if (path.isEmpty() && indexAvailable) {
                    StringBuilder html = new StringBuilder("<html><body>");
                    for (String file : files.keySet()) {
                        html.append("<a href=\"./").append(file).append("\">").append(file).append("</a>");
                    }
                    response = html.append("</body></html>").toString().getBytes(StandardCharsets.UTF_8);
                } else {
                    response = files.get(path);
                }
                if (response == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
