package i18nautoupdatemod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import i18nautoupdatemod.entity.GameMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void mergesOverlayTranslationsOnTopOfStablePack() throws Exception {
        Path resourcePacks = temporaryDirectory.resolve("resourcepacks");
        Path sourceCache = temporaryDirectory.resolve("source-cache");
        Files.createDirectories(sourceCache);
        Files.createDirectories(resourcePacks);
        writeZip(sourceCache.resolve("stable.zip"),
                "pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"stable\"}}",
                "assets/alpha/lang/zh_cn.json", "{\"a.one\":\"stable one\",\"a.two\":\"stable two\"}",
                "assets/empty/lang/zh_cn.json", "{\"e.key\":\"kept\"}",
                "assets/legacy/lang/zh_CN.lang", "# comment\nl.one=stable one\nl.two=stable two\n",
                "assets/skipped/lang/zh_cn.json", "{\"s\":\"s\"}");
        Path overlay = temporaryDirectory.resolve("beta.zip");
        writeZip(overlay,
                "pack.mcmeta", "{\"pack\":{\"pack_format\":99,\"description\":\"beta\"}}",
                "assets/alpha/lang/zh_cn.json", "{\"a.two\":\"beta <two>\",\"a.three\":\"beta three\"}",
                "assets/empty/lang/zh_cn.json", "{}",
                "assets/legacy/lang/zh_CN.lang", "l.two=beta two\nl.three=beta three\n",
                "assets/fresh/lang/zh_cn.json", "{\"f.key\":\"fresh\"}",
                "assets/skipped/lang/zh_cn.json", "{\"s\":\"beta\"}");

        ResourcePack source = new ResourcePack("stable.zip", resourcePacks, sourceCache);
        ResourcePackConverter converter = new ResourcePackConverter(
                Collections.singletonList(source),
                Collections.singletonList(overlay),
                "converted.zip",
                temporaryDirectory.resolve("converted-cache"),
                resourcePacks);
        GameMetaData metadata = new GameMetaData();
        metadata.packFormat = 15;
        converter.convert(metadata, "description",
                new HashSet<>(Arrays.asList("alpha", "empty", "legacy", "fresh")));

        try (ZipFile zip = new ZipFile(resourcePacks.resolve("converted.zip").toFile())) {
            JsonObject alpha = json(zip, "assets/alpha/lang/zh_cn.json");
            assertEquals("stable one", alpha.get("a.one").getAsString());
            assertEquals("beta <two>", alpha.get("a.two").getAsString());
            assertEquals("beta three", alpha.get("a.three").getAsString());
            assertTrue(text(zip, "assets/alpha/lang/zh_cn.json").contains("<two>"));
            assertEquals("kept", json(zip, "assets/empty/lang/zh_cn.json").get("e.key").getAsString());
            assertEquals("fresh", json(zip, "assets/fresh/lang/zh_cn.json").get("f.key").getAsString());
            assertEquals("# comment\nl.one=stable one\nl.two=beta two\nl.three=beta three\n",
                    text(zip, "assets/legacy/lang/zh_CN.lang"));
            assertNull(zip.getEntry("assets/skipped/lang/zh_cn.json"));
            assertEquals(15, json(zip, "pack.mcmeta").getAsJsonObject("pack").get("pack_format").getAsInt());
        }
    }

    private static void writeZip(Path file, String... entries) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < entries.length; i += 2) {
                output.putNextEntry(new ZipEntry(entries[i]));
                output.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static String text(ZipFile zip, String name) throws Exception {
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) {
            return new String(IOUtils.toByteArray(input), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(ZipFile zip, String name) throws Exception {
        return JsonParser.parseString(text(zip, name)).getAsJsonObject();
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
