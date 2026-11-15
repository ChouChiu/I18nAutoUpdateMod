package i18nautoupdatemod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import i18nautoupdatemod.entity.GameMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackConverterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesLegacyPackFormatAndPreservesUnrelatedMetadata() throws Exception {
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
    void writesModernMinMaxFormatWithoutLegacyFields() throws Exception {
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
                resourcePacks.resolve("converted.zip"));
        GameMetaData metadata = new GameMetaData();
        metadata.packFormat = 64;

        assertThrows(IOException.class, () -> converter.convert(
                metadata, "description", new HashSet<>()));
        assertArrayEquals(existing, Files.readAllBytes(resourcePacks.resolve("converted.zip")));
    }

    @Test
    void rejectsTraversalEntriesAndLeavesInstalledPackIntact() throws Exception {
        Path cache = Files.createDirectory(temporaryDirectory.resolve("cache"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(cache.resolve("source.zip")))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("assets/example/../translations.lang"));
            zip.write(1);
        }
        Path installed = temporaryDirectory.resolve("converted.zip");
        byte[] existing = "installed".getBytes(StandardCharsets.UTF_8);
        Files.write(installed, existing);

        ResourcePack source = new ResourcePack("source.zip", temporaryDirectory, cache);
        ResourcePackConverter converter = new ResourcePackConverter(Collections.singletonList(source), installed);
        assertThrows(IOException.class, () -> converter.convert(new GameMetaData(), "test", Collections.emptySet()));
        assertArrayEquals(existing, Files.readAllBytes(installed));
        try (java.util.stream.Stream<Path> files = Files.list(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void writesPackMetaAsAscii() {
        byte[] output = ResourcePackConverter.toAsciiJson("{\"description\":\"作者\"}");

        for (byte value : output) {
            assertTrue(value >= 0);
        }
        assertEquals("作者", JsonParser.parseString(new String(output, StandardCharsets.US_ASCII))
                .getAsJsonObject().get("description").getAsString());
    }

    private static JsonObject convert(String input, GameMetaData metadata) throws Exception {
        byte[] output = ResourcePackConverter.convertPackMeta(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                metadata,
                "new description");
        return JsonParser.parseString(new String(output, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
