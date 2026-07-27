package i18nautoupdatemod.util;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationDetectUtilTest {
    @Test
    void parsesSupportedGeoApiResponses() {
        assertTrue(LocationDetectUtil.parseResponse("{\"flag\":1}"));
        assertTrue(LocationDetectUtil.parseResponse("{\"flag\":true}"));
        assertFalse(LocationDetectUtil.parseResponse("{\"flag\":0}"));
        assertTrue(LocationDetectUtil.parseResponse("{\"country_code\":\"CN\"}"));
        assertFalse(LocationDetectUtil.parseResponse("{\"country_code\":\"US\"}"));
        assertNull(LocationDetectUtil.parseResponse("{\"other\":true}"));
    }

    @Test
    void failedGeoApiFallsBackToNextApi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/failed", exchange -> {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            });
            byte[] response = "{\"country_code\":\"CN\"}".getBytes("UTF-8");
            server.createContext("/working", exchange -> {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            String root = "http://127.0.0.1:" + server.getAddress().getPort();
            assertTrue(LocationDetectUtil.detectMainlandChina(
                    new String[]{root + "/failed", root + "/working"}));
        } finally {
            server.stop(0);
        }
    }
}
