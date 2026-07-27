package i18nautoupdatemod;

import com.google.gson.Gson;
import i18nautoupdatemod.core.GameConfig;
import i18nautoupdatemod.core.I18nConfig;
import i18nautoupdatemod.core.ResourcePack;
import i18nautoupdatemod.core.ResourcePackConverter;
import i18nautoupdatemod.entity.GameAssetDetail;
import i18nautoupdatemod.entity.GameMetaData;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.Version;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class I18nAutoUpdateMod {
    public static final String MOD_ID = "i18nautoupdatemod";
    public static final String OLD_MOD_ID = "i18nupdatemod";
    public static final String MOD_VERSION = implementationVersion();
    public static final Gson GSON = new Gson();

    private static final Object UPDATE_LOCK = new Object();
    private static final ExecutorService UPDATE_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "i18nautoupdatemod-updater");
                thread.setDaemon(true);
                return thread;
            });
    private static CompletableFuture<Void> updateFuture;

    private I18nAutoUpdateMod() {
    }

    public static CompletableFuture<Void> init(
            Path minecraftPath,
            String minecraftVersion,
            String loader,
            @NotNull HashSet<String> modDomains
    ) {
        HashSet<String> domains = new HashSet<>(modDomains);
        domains.remove(MOD_ID);
        domains.remove(OLD_MOD_ID);

        Log.info("I18nAutoUpdateMod %s is loaded in %s with %s",
                MOD_VERSION, minecraftVersion, loader);
        Log.debug("Minecraft path: %s", minecraftPath);

        if (isNetEaseMinecraft()) {
            logNetEaseWarning();
            return CompletableFuture.completedFuture(null);
        }

        String convertedFileName =
                String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        registerResourcePack(minecraftPath, minecraftVersion, convertedFileName);

        return startAsyncOnce(() -> updateResourcePack(
                minecraftPath, minecraftVersion, loader, domains));
    }

    static CompletableFuture<Void> startAsyncOnce(Runnable update) {
        synchronized (UPDATE_LOCK) {
            if (updateFuture != null) {
                return updateFuture;
            }
            updateFuture = CompletableFuture.runAsync(update, UPDATE_EXECUTOR);
            updateFuture.whenComplete((ignored, error) -> {
                if (error != null) {
                    Log.warning("Background resource pack update failed: %s", error);
                } else {
                    Log.info("Background resource pack update completed");
                }
            });
            return updateFuture;
        }
    }

    private static void updateResourcePack(
            Path minecraftPath,
            String minecraftVersion,
            String loader,
            HashSet<String> modDomains
    ) {
        try {
            Path storagePath = prepareStoragePath();
            Path resourcePackDirectory = minecraftPath.resolve("resourcepacks");
            FileUtil.ensureDirectory(resourcePackDirectory);
            Log.debug("Local storage path: %s", storagePath);

            GameAssetDetail assets = I18nConfig.getAssetDetail(minecraftVersion, loader);
            List<ResourcePack> languagePacks = new ArrayList<>();
            for (GameAssetDetail.AssetDownloadDetail detail : assets.downloads) {
                ResourcePack languagePack = new ResourcePack(
                        detail.fileName,
                        resourcePackDirectory,
                        storagePath.resolve(detail.targetVersion));
                languagePack.checkUpdate(detail.sources);
                languagePacks.add(languagePack);
            }

            GameMetaData metaData = I18nConfig.getPackFormat(minecraftVersion);
            ResourcePackConverter converter = new ResourcePackConverter(
                    languagePacks,
                    assets.convertedFileName,
                    storagePath.resolve(minecraftVersion),
                    resourcePackDirectory);
            converter.convert(
                    metaData,
                    getResourcePackDescription(assets.downloads),
                    modDomains);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update resource pack", e);
        }
    }

    private static void registerResourcePack(
            Path minecraftPath, String minecraftVersion, String convertedFileName) {
        try {
            GameConfig config = new GameConfig(minecraftPath.resolve("options.txt"));
            config.addResourcePack(
                    "Minecraft-Mod-Language-Modpack",
                    resourcePackId(minecraftVersion, convertedFileName));
            config.writeToFile();
        } catch (Exception e) {
            Log.warning("Failed to register resource pack for the next launch: %s", e);
        }
    }

    static String resourcePackId(String minecraftVersion, String convertedFileName) {
        Version version = Version.from(minecraftVersion);
        Version filePrefixVersion = Version.from("1.13");
        boolean usesModernPackId =
                version != null
                        && filePrefixVersion != null
                        && version.compareTo(filePrefixVersion) >= 0;
        return (usesModernPackId ? "file/" : "") + convertedFileName;
    }

    private static String getResourcePackDescription(
            List<GameAssetDetail.AssetDownloadDetail> downloads) {
        if (downloads.size() > 1) {
            return String.format(
                    "该包由%s版本合并\n作者：CFPA团队及汉化项目贡献者",
                    downloads.stream()
                            .map(detail -> detail.targetVersion)
                            .collect(Collectors.joining("和")));
        }
        return String.format(
                "该包对应的官方支持版本为%s\n作者：CFPA团队及汉化项目贡献者",
                downloads.get(0).targetVersion);
    }

    public static Path prepareStoragePath() throws Exception {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path platformBase = getPlatformStorageBase(userHome);
        Path homeNew = userHome.resolve("." + MOD_ID);
        Path homeOld = userHome.resolve("." + OLD_MOD_ID);
        Path platformNew = platformBase.resolve("." + MOD_ID);
        Path platformOld = platformBase.resolve("." + OLD_MOD_ID);

        Path target = Files.exists(homeNew) || Files.exists(homeOld) ? homeNew : platformNew;
        for (Path legacy : Stream.of(homeOld, platformOld)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList())) {
            FileUtil.migrateDirectory(legacy, target);
        }
        FileUtil.ensureDirectory(target);
        return target;
    }

    private static Path getPlatformStorageBase(Path userHome) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac") || osName.contains("os x")) {
            return userHome.resolve("Library/Application Support");
        }

        String localAppData = System.getenv("LocalAppData");
        if (localAppData != null && !localAppData.trim().isEmpty()) {
            return Paths.get(localAppData);
        }

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome != null && !xdgDataHome.trim().isEmpty()) {
            return Paths.get(xdgDataHome);
        }
        return userHome.resolve(".local/share");
    }

    private static String implementationVersion() {
        Package modPackage = I18nAutoUpdateMod.class.getPackage();
        String version = modPackage == null ? null : modPackage.getImplementationVersion();
        return version == null || version.trim().isEmpty() ? "development" : version;
    }

    private static boolean isNetEaseMinecraft() {
        try {
            Class.forName("com.netease.mc.mod.network.common.Library");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static void logNetEaseWarning() {
        Log.warning("I18nAutoUpdateMod downloads an uncontrolled resource pack from the Internet.");
        Log.warning("To comply with the NetEase content review rules, nothing will be downloaded.");
        Log.warning("I18nAutoUpdateMod会从互联网获取内容不可控的资源包。");
        Log.warning("为了遵循网易我的世界开发者内容审核制度，I18nAutoUpdateMod不会下载任何内容。");
    }

    static void resetUpdateForTests() {
        synchronized (UPDATE_LOCK) {
            updateFuture = null;
        }
    }
}
