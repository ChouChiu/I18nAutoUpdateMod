package i18nautoupdatemod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import i18nautoupdatemod.entity.GameMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackConverterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesLegacyPackFormatAndPreservesUnrelatedMetadata() {
        String input = "{"
                + "\"pack\":{\"description\":\"old\",\"pack_format\":99,"
                + "\"min_format\":69,\"max_format\":75,\"supported_formats\":[1,99],"
                + "\"custom\":\"kept\"},"
                + "\"filter\":{\"block\":[{\"namespace\":\"example\"}]},"
                + "\"language\":{\"test\":{\"name\":\"Test\",\"region\":\"Test\","
                + "\"bidirectional\":false}}}";
        GameMetaData metadata = new GameMetaData();
        metadata.packFormat = 22;

        JsonObject converted = convert(input, metadata);
        JsonObject pack = converted.getAsJsonObject("pack");
        assertEquals(22, pack.get("pack_format").getAsInt());
        assertEquals("new description", pack.get("description").getAsString());
        assertEquals("kept", pack.get("custom").getAsString());
        assertFalse(pack.has("min_format"));
        assertFalse(pack.has("max_format"));
        assertFalse(pack.has("supported_formats"));
        assertTrue(converted.has("filter"));
        assertTrue(converted.has("language"));
    }

    @Test
    void writesModernMinMaxFormatWithoutLegacyFields() {
        String input = "{\"pack\":{\"description\":\"old\",\"pack_format\":64,"
                + "\"supported_formats\":[64,75]},\"overlays\":{\"entries\":[]}}";
        GameMetaData metadata = new GameMetaData();
        metadata.minFormat = 69;
        metadata.maxFormat = 75;

        JsonObject converted = convert(input, metadata);
        JsonObject pack = converted.getAsJsonObject("pack");
        assertFalse(pack.has("pack_format"));
        assertFalse(pack.has("supported_formats"));
        assertEquals(69, pack.get("min_format").getAsInt());
        assertEquals(75, pack.get("max_format").getAsInt());
        assertTrue(converted.has("overlays"));
    }

    @Test
    void failedConversionDoesNotReplaceExistingResourcePack() throws Exception {
        Path resourcePacks = temporaryDirectory.resolve("resourcepacks");
        Path sourceCache = temporaryDirectory.resolve("source-cache");
        Files.createDirectories(sourceCache);
        Files.createDirectories(resourcePacks);
        Files.write(sourceCache.resolve("source.zip"), "not a zip".getBytes(StandardCharsets.UTF_8));
        byte[] existing = "existing pack".getBytes(StandardCharsets.UTF_8);
        Files.write(resourcePacks.resolve("converted.zip"), existing);

        ResourcePack source = new ResourcePack("source.zip", resourcePacks, sourceCache);
        ResourcePackConverter converter = new ResourcePackConverter(
                Collections.singletonList(source),
                "converted.zip",
                temporaryDirectory.resolve("converted-cache"),
                resourcePacks);
        GameMetaData metadata = new GameMetaData();
        metadata.packFormat = 64;

        assertThrows(Exception.class, () -> converter.convert(
                metadata, "description", new HashSet<>()));
        assertArrayEquals(existing, Files.readAllBytes(resourcePacks.resolve("converted.zip")));
    }

    private static JsonObject convert(String input, GameMetaData metadata) {
        byte[] output = ResourcePackConverter.convertPackMeta(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                metadata,
                "new description");
        return JsonParser.parseString(new String(output, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
