package i18nautoupdatemod.core;

import com.google.gson.Gson;
import i18nautoupdatemod.entity.GameAssetDetail;
import i18nautoupdatemod.entity.GameMapping;
import i18nautoupdatemod.entity.GameMetaData;
import i18nautoupdatemod.entity.I18nMetaData;
import i18nautoupdatemod.util.AssetUtil;
import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.Version;
import i18nautoupdatemod.util.VersionRange;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class I18nConfig {
    private static final Gson GSON = new Gson();
    private static final I18nMetaData META_DATA = loadMetaData();

    private I18nConfig() {
    }

    private static I18nMetaData loadMetaData() {
        try (InputStream input = I18nConfig.class.getResourceAsStream("/i18nMetaData.json")) {
            if (input == null) {
                throw new IllegalStateException("i18nMetaData.json is missing");
            }
            I18nMetaData result = GSON.fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8), I18nMetaData.class);
            if (result == null || result.gameMappings == null || result.assets == null) {
                throw new IllegalStateException("i18nMetaData.json is incomplete");
            }
            return result;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static GameMapping getGameMapping(String minecraftVersion) {
        Version version = Version.from(minecraftVersion);
        return META_DATA.gameMappings.stream()
                .filter(mapping -> mapping.versions.stream()
                        .anyMatch(item -> new VersionRange(item.range).contains(version)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format("Version %s not found in i18n metadata", minecraftVersion)));
    }

    public static GameMetaData getPackFormat(String minecraftVersion) {
        Version version = Version.from(minecraftVersion);
        return getGameMapping(minecraftVersion).versions.stream()
                .filter(item -> new VersionRange(item.range).contains(version))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        String.format("Pack format for %s not found", minecraftVersion)));
    }

    public static GameAssetDetail getAssetDetail(String minecraftVersion, String loader) {
        return getAssetDetail(minecraftVersion, loader, ModConfig.defaults());
    }

    public static GameAssetDetail getAssetDetail(
            String minecraftVersion, String loader, ModConfig config) {
        GameMapping mapping = getGameMapping(minecraftVersion);
        GameAssetDetail result = new GameAssetDetail();
        result.downloads = new ArrayList<>();

        String assetVariant = normalizeLoader(loader);
        for (String packVersion : mapping.packs) {
            Map<String, String> variants = META_DATA.assets.get(packVersion);
            if (variants == null || variants.isEmpty()) {
                throw new IllegalStateException("Asset metadata missing for " + packVersion);
            }

            String selectedVariant = selectVariant(variants, assetVariant);
            List<String> selectedVariants = new ArrayList<>();
            selectedVariants.add(selectedVariant);
            if (config.mergeLoaders) {
                // The current loader's variant comes first so it wins on conflicts.
                for (String variant : variants.keySet()) {
                    if (!selectedVariants.contains(variant)) {
                        selectedVariants.add(variant);
                    }
                }
            }

            for (String variant : selectedVariants) {
                GameAssetDetail.AssetDownloadDetail detail = new GameAssetDetail.AssetDownloadDetail();
                detail.targetVersion = packVersion;
                detail.fileName = variants.get(variant);
                detail.checksumFileName = checksumFileName(packVersion, variant);
                detail.sources = AssetUtil.resolveSources(
                        detail.fileName, detail.checksumFileName, config);
                result.downloads.add(detail);
            }
        }

        result.convertedFileName =
                String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        return result;
    }

    static String normalizeLoader(String loader) {
        String normalized = loader == null ? "" : loader.toLowerCase(Locale.ROOT);
        return normalized.contains("fabric") || normalized.contains("quilt") ? "Fabric" : "Forge";
    }

    static String checksumFileName(String packVersion, String variant) {
        return packVersion + ("Fabric".equalsIgnoreCase(variant) ? "-fabric" : "") + ".md5";
    }

    static String selectVariant(Map<String, String> variants, String target) {
        for (String variant : variants.keySet()) {
            if (variant.equalsIgnoreCase(target)) {
                return variant;
            }
        }
        for (String variant : variants.keySet()) {
            if (variant.equalsIgnoreCase("Forge")) {
                return variant;
            }
        }
        Log.warning("No %s or Forge variant in %s", target, variants.keySet());
        return variants.keySet().iterator().next();
    }

    static I18nMetaData getMetaDataForTests() {
        return META_DATA;
    }
}
