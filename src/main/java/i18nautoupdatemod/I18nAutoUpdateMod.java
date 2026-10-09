package i18nautoupdatemod;

import com.google.gson.Gson;
import i18nautoupdatemod.core.BetaResourcePack;
import i18nautoupdatemod.core.GameConfig;
import i18nautoupdatemod.core.I18nConfig;
import i18nautoupdatemod.core.ModConfig;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class I18nAutoUpdateMod {
    public static final String MOD_ID = "i18nautoupdatemod";
    public static final String OLD_MOD_ID = "i18nupdatemod";
    public static final String MOD_VERSION = implementationVersion();
    public static final Gson GSON = new Gson();

    public static final String INITIAL_TIMEOUT_PROPERTY = "i18nautoupdatemod.initialTimeout";
    public static final int DEFAULT_INITIAL_DOWNLOAD_TIMEOUT_SECONDS = ModConfig.DEFAULT_INITIAL_TIMEOUT_SECONDS;

    private static final Object UPDATE_LOCK = new Object();
    private static final ExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "i18nautoupdatemod-updater");
        thread.setDaemon(true);
        return thread;
    });
    private static CompletableFuture<Void> updateFuture;

    private I18nAutoUpdateMod() {
    }

    static int getInitialDownloadTimeoutSeconds() {
        return getInitialDownloadTimeoutSeconds(DEFAULT_INITIAL_DOWNLOAD_TIMEOUT_SECONDS);
    }

    /**
     * The JVM property takes precedence over the configured value.
     */
    static int getInitialDownloadTimeoutSeconds(int configured) {
        int fallback = Math.max(0, configured);
        String property = System.getProperty(INITIAL_TIMEOUT_PROPERTY);
        if (property == null || property.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(property.trim()));
        } catch (NumberFormatException e) {
            Log.warning("Invalid %s value '%s', using %d seconds: %s",
                    INITIAL_TIMEOUT_PROPERTY, property, fallback, e);
            return fallback;
        }
    }

    public static CompletableFuture<Void> init(
            Path minecraftPath,
            String minecraftVersion,
            String loader,
            @NotNull HashSet<String> modDomains) {
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

        ModConfig config = ModConfig.load(minecraftPath);

        String convertedFileName = String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        Path convertedPackPath = minecraftPath.resolve("resourcepacks").resolve(convertedFileName);
        boolean packAlreadyExists = Files.exists(convertedPackPath);

        if (packAlreadyExists) {
            registerResourcePack(minecraftPath, minecraftVersion, convertedFileName, config);
        }

        CompletableFuture<Void> future = startAsyncOnce(() -> updateResourcePack(
                minecraftPath, minecraftVersion, loader, domains, config));

        if (!packAlreadyExists) {
            int timeoutSeconds = getInitialDownloadTimeoutSeconds(config.initialTimeout);
            if (timeoutSeconds > 0) {
                Log.info(
                        "First launch detected (resource pack missing). Waiting up to %d seconds for initial generation...",
                        timeoutSeconds);
                try {
                    future.get(timeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    Log.warning(
                            "Initial resource pack download timed out after %d seconds; continuing game launch. The pack will take effect on next launch.",
                            timeoutSeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.warning("Initial resource pack download interrupted: %s", e);
                } catch (Exception e) {
                    Log.warning("Initial resource pack download failed: %s", e);
                }
            }
            if (Files.exists(convertedPackPath)) {
                registerResourcePack(minecraftPath, minecraftVersion, convertedFileName, config);
            }
        }

        return future;
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
            HashSet<String> modDomains,
            ModConfig config) {
        try {
            Path storagePath = prepareStoragePath();
            Path resourcePackDirectory = minecraftPath.resolve("resourcepacks");
            FileUtil.ensureDirectory(resourcePackDirectory);
            Log.debug("Local storage path: %s", storagePath);

            GameAssetDetail assets = I18nConfig.getAssetDetail(minecraftVersion, loader, config);
            List<ResourcePack> languagePacks = new ArrayList<>();
            for (GameAssetDetail.AssetDownloadDetail detail : assets.downloads) {
                ResourcePack languagePack = new ResourcePack(
                        detail.fileName,
                        resourcePackDirectory,
                        storagePath.resolve(detail.targetVersion));
                languagePack.checkUpdate(detail.sources);
                languagePacks.add(languagePack);
            }

            List<Path> betaPacks = new ArrayList<>();
            if (config.betaPack) {
                BetaResourcePack beta = new BetaResourcePack(storagePath.resolve("beta"));
                for (String targetVersion : targetVersions(assets.downloads)) {
                    if (config.mergeLoaders) {
                        betaPacks.addAll(beta.resolveAll(targetVersion, loader));
                    } else {
                        Path betaPack = beta.resolve(targetVersion, loader);
                        if (betaPack != null) {
                            betaPacks.add(betaPack);
                        }
                    }
                }
            }

            GameMetaData metaData = I18nConfig.getPackFormat(minecraftVersion);
            ResourcePackConverter converter = new ResourcePackConverter(
                    languagePacks,
                    betaPacks,
                    assets.convertedFileName,
                    storagePath.resolve(minecraftVersion),
                    resourcePackDirectory);
            converter.convert(
                    metaData,
                    getResourcePackDescription(assets.downloads, !betaPacks.isEmpty()),
                    modDomains);
            registerResourcePack(minecraftPath, minecraftVersion, assets.convertedFileName, config);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update resource pack", e);
        }
    }

    private static void registerResourcePack(
            Path minecraftPath, String minecraftVersion, String convertedFileName, ModConfig config) {
        try {
            GameConfig options = new GameConfig(minecraftPath.resolve("options.txt"));
            options.addResourcePack(
                    "Minecraft-Mod-Language-Modpack",
                    resourcePackId(minecraftVersion, convertedFileName),
                    config.forceBottom);
            options.writeToFile();
        } catch (Exception e) {
            Log.warning("Failed to register resource pack: %s", e);
        }
    }

    static String resourcePackId(String minecraftVersion, String convertedFileName) {
        Version version = Version.from(minecraftVersion);
        Version filePrefixVersion = Version.from("1.13");
        boolean usesModernPackId = version != null
                && filePrefixVersion != null
                && version.compareTo(filePrefixVersion) >= 0;
        return (usesModernPackId ? "file/" : "") + convertedFileName;
    }

    static String getResourcePackDescription(
            List<GameAssetDetail.AssetDownloadDetail> downloads, boolean withBeta) {
        String description = getResourcePackDescription(downloads);
        return withBeta ? description + "\n（含 Beta 预览翻译）" : description;
    }

    private static String getResourcePackDescription(
            List<GameAssetDetail.AssetDownloadDetail> downloads) {
        List<String> versions = targetVersions(downloads);
        if (versions.size() > 1) {
            return String.format(
                    "该包由%s版本合并\n作者：CFPA团队及汉化项目贡献者",
                    String.join("和", versions));
        }
        return String.format(
                "该包对应的官方支持版本为%s\n作者：CFPA团队及汉化项目贡献者",
                versions.get(0));
    }

    private static List<String> targetVersions(List<GameAssetDetail.AssetDownloadDetail> downloads) {
        return downloads.stream()
                .map(detail -> detail.targetVersion)
                .distinct()
                .collect(Collectors.toList());
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
