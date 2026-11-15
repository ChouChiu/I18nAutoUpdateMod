package i18nautoupdatemod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {
    @TempDir
    Path minecraftPath;

    @Test
    void writesDefaultsOnFirstLoad() throws Exception {
        ModConfig config = ModConfig.load(minecraftPath);

        assertTrue(config.forceBottom);
        assertFalse(config.betaPack);
        assertNull(config.normalizedDefaultSource());
        assertEquals(10, config.initialTimeout);
        JsonObject written = read();
        assertTrue(written.get("forceBottom").getAsBoolean());
        assertEquals("auto", written.get("defaultSource").getAsString());
        assertEquals(3, written.getAsJsonArray("mirrorPriority").size());
    }

    @Test
    void fillsMissingFieldsAndKeepsUserValues() throws Exception {
        write("{\"betaPack\":true,\"forceBottom\":false,\"customSetting\":42}");

        ModConfig config = ModConfig.load(minecraftPath);

        assertTrue(config.betaPack);
        assertFalse(config.forceBottom);
        assertEquals("auto", config.defaultSource);
        JsonObject written = read();
        assertTrue(written.get("betaPack").getAsBoolean());
        assertTrue(written.has("mirrorPriority"));
        assertTrue(written.has("initialTimeout"));
        assertEquals(42, written.get("customSetting").getAsInt());
    }

    @Test
    void invalidFileFallsBackToDefaultsWithoutOverwriting() throws Exception {
        String broken = "{\"betaPack\": tru";
        write(broken);

        ModConfig config = ModConfig.load(minecraftPath);

        assertFalse(config.betaPack);
        assertEquals(broken, new String(Files.readAllBytes(file()), StandardCharsets.UTF_8));
    }

    @Test
    void normalizesSourceNames() {
        ModConfig config = ModConfig.defaults();
        config.defaultSource = "community";
        config.mirrorPriority = Arrays.asList("github", "unknown", "GitHub");

        assertEquals("Community Mirror", config.normalizedDefaultSource());
        assertEquals(Arrays.asList("GitHub", "CFPA", "Community Mirror"),
                config.normalizedMirrorPriority());

        config.defaultSource = "nowhere";
        assertNull(config.normalizedDefaultSource());
    }

    @Test
    void readsUserOptionsPortedFromUpstream() throws Exception {
        write("{\"cacheTtlHours\":0,\"excludeMods\":[\" Create \",\"\",null],"
                + "\"onlyWhenChineseSystem\":true}");

        ModConfig config = ModConfig.load(minecraftPath);

        assertEquals(0, config.cacheTtlMillis());
        assertEquals(new HashSet<>(Arrays.asList("create")), config.normalizedExcludeMods());
        assertTrue(config.onlyWhenChineseSystem);
        assertTrue(ModConfig.isChineseLocale(Locale.SIMPLIFIED_CHINESE));
        assertTrue(ModConfig.isChineseLocale(Locale.TRADITIONAL_CHINESE));
        assertFalse(ModConfig.isChineseLocale(Locale.US));
    }

    @Test
    void defaultsMatchPreviousBehavior() {
        ModConfig config = ModConfig.defaults();

        assertEquals(TimeUnit.DAYS.toMillis(1), config.cacheTtlMillis());
        assertTrue(config.normalizedExcludeMods().isEmpty());
        assertTrue(config.isLanguageAllowed());
    }

    private Path file() {
        return minecraftPath.resolve("config").resolve(ModConfig.FILE_NAME);
    }

    private void write(String content) throws Exception {
        Files.createDirectories(file().getParent());
        Files.write(file(), content.getBytes(StandardCharsets.UTF_8));
    }

    private JsonObject read() throws Exception {
        return JsonParser.parseString(new String(Files.readAllBytes(file()), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
