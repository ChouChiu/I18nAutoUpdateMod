package i18nautoupdatemod.core;

import i18nautoupdatemod.entity.GameAssetDetail;
import i18nautoupdatemod.entity.GameMapping;
import i18nautoupdatemod.entity.GameMetaData;
import i18nautoupdatemod.entity.I18nMetaData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nConfigTest {
    @Test
    void mergeLoadersDownloadsEveryVariantWithCurrentLoaderFirst() {
        ModConfig config = ModConfig.defaults();
        config.defaultSource = "GitHub";

        GameAssetDetail single = I18nConfig.getAssetDetail("1.17.1", "Fabric", config);
        assertEquals(Arrays.asList(
                        "Minecraft-Mod-Language-Modpack-1-18-Fabric.zip",
                        "Minecraft-Mod-Language-Modpack-1-16-Fabric.zip"),
                fileNames(single));

        config.mergeLoaders = true;
        GameAssetDetail fabric = I18nConfig.getAssetDetail("1.17.1", "Fabric", config);
        assertEquals(Arrays.asList(
                        "Minecraft-Mod-Language-Modpack-1-18-Fabric.zip",
                        "Minecraft-Mod-Language-Modpack-1-18.zip",
                        "Minecraft-Mod-Language-Modpack-1-16-Fabric.zip",
                        "Minecraft-Mod-Language-Modpack-1-16.zip"),
                fileNames(fabric));
        assertEquals("1.18-fabric.md5", fabric.downloads.get(0).checksumFileName);
        assertEquals("1.18.md5", fabric.downloads.get(1).checksumFileName);

        GameAssetDetail forge = I18nConfig.getAssetDetail("1.20.1", "Forge", config);
        assertEquals(Arrays.asList(
                        "Minecraft-Mod-Language-Modpack-1-20.zip",
                        "Minecraft-Mod-Language-Modpack-1-20-Fabric.zip",
                        "Minecraft-Mod-Language-Modpack-1-19.zip",
                        "Minecraft-Mod-Language-Modpack-1-18.zip",
                        "Minecraft-Mod-Language-Modpack-1-18-Fabric.zip"),
                fileNames(forge));

        // Versions without a Fabric pack are unaffected.
        GameAssetDetail legacy = I18nConfig.getAssetDetail("1.12.2", "Forge", config);
        assertEquals(1, legacy.downloads.size());
    }

    private static List<String> fileNames(GameAssetDetail detail) {
        return detail.downloads.stream().map(it -> it.fileName).collect(Collectors.toList());
    }

    @Test
    void groupedMetadataReferencesKnownAssetsAndValidFormats() {
        I18nMetaData metadata = I18nConfig.getMetaDataForTests();
        assertFalse(metadata.gameMappings.isEmpty());
        assertFalse(metadata.assets.isEmpty());

        for (GameMapping mapping : metadata.gameMappings) {
            assertFalse(mapping.packs.isEmpty());
            assertFalse(mapping.versions.isEmpty());
            for (String pack : mapping.packs) {
                assertNotNull(metadata.assets.get(pack), "Missing asset " + pack);
            }
            for (GameMetaData version : mapping.versions) {
                assertNotNull(version.range);
                assertTrue(version.useNewFormat()
                        || version.packFormat != null,
                        "Missing pack format for " + version.range);
            }
        }
    }

    @Test
    void resolvesPackFormatsAtEveryBoundary() {
        assertFormat("1.6.1", 1);
        assertFormat("1.8.9", 1);
        assertFormat("1.9", 2);
        assertFormat("1.12.2", 3);
        assertFormat("1.14.4", 4);
        assertFormat("1.16.1", 5);
        assertFormat("1.16.5", 6);
        assertFormat("1.17.1", 7);
        assertFormat("1.18.2", 8);
        assertFormat("1.19.2", 9);
        assertFormat("1.19.3", 12);
        assertFormat("1.19.4", 13);
        assertFormat("1.20.1", 15);
        assertFormat("1.20.2", 18);
        assertFormat("1.20.4", 22);
        assertFormat("1.20.6", 32);
        assertFormat("1.21.1", 34);
        assertFormat("1.21.3", 42);
        assertFormat("1.21.4", 46);
        assertFormat("1.21.5", 55);
        assertFormat("1.21.6", 63);
        assertFormat("1.21.8", 64);

        GameMetaData newest = I18nConfig.getPackFormat("1.21.11");
        assertTrue(newest.useNewFormat());
        assertEquals(69, newest.minFormat);
        assertEquals(75, newest.maxFormat);

        GameMetaData newest26 = I18nConfig.getPackFormat("26.3");
        assertTrue(newest26.useNewFormat());
        assertEquals(84, newest26.minFormat);
        assertEquals(97, newest26.maxFormat);
    }

    @Test
    void normalizesLoaderFamiliesAndChecksumNames() {
        List<String> fabricFamily = Arrays.asList("Fabric", "fabric-loader", "Quilt");
        for (String loader : fabricFamily) {
            assertEquals("Fabric", I18nConfig.normalizeLoader(loader));
        }
        assertEquals("Forge", I18nConfig.normalizeLoader("Forge"));
        assertEquals("Forge", I18nConfig.normalizeLoader("NeoForge"));
        assertEquals("1.21-fabric.md5",
                I18nConfig.checksumFileName("1.21", "Fabric"));
        assertEquals("1.21.md5",
                I18nConfig.checksumFileName("1.21", "Forge"));
    }

    @Test
    void fallsBackToForgeWhenDedicatedLoaderVariantIsMissing() {
        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("Forge", "forge.zip");
        assertEquals("Forge", I18nConfig.selectVariant(variants, "Fabric"));
        assertEquals("Forge", I18nConfig.selectVariant(
                variants, I18nConfig.normalizeLoader("NeoForge")));

        variants.put("Fabric", "fabric.zip");
        assertEquals("Fabric", I18nConfig.selectVariant(
                variants, I18nConfig.normalizeLoader("Quilt")));
    }

    @Test
    void everyDeclaredMinecraftVersionHasAMapping() {
        String versions = "1.6.1,1.6.2,1.6.4,1.7.2,1.7.10,1.8,1.8.8,1.8.9,"
                + "1.9,1.9.4,1.10,1.10.2,1.11,1.11.2,1.12,1.12.1,1.12.2,"
                + "1.13.2,1.14,1.14.1,1.14.2,1.14.3,1.14.4,"
                + "1.15,1.15.1,1.15.2,1.16,1.16.1,1.16.2,1.16.3,1.16.4,1.16.5,"
                + "1.17,1.17.1,1.18,1.18.1,1.18.2,"
                + "1.19,1.19.1,1.19.2,1.19.3,1.19.4,"
                + "1.20,1.20.1,1.20.2,1.20.3,1.20.4,1.20.5,1.20.6,"
                + "1.21,1.21.1,1.21.2,1.21.3,1.21.4,1.21.5,1.21.6,"
                + "1.21.7,1.21.8,1.21.9,1.21.10,1.21.11,"
                + "26.1,26.1.2,26.2,26.3";
        for (String version : versions.split(",")) {
            assertNotNull(I18nConfig.getPackFormat(version), version);
        }
    }

    private static void assertFormat(String minecraftVersion, int expected) {
        assertEquals(expected, I18nConfig.getPackFormat(minecraftVersion).packFormat);
    }
}
