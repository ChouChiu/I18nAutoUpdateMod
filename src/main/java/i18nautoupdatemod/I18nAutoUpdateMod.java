package i18nautoupdatemod;

import com.google.gson.JsonParseException;
import i18nautoupdatemod.core.GameConfig;
import i18nautoupdatemod.core.I18nConfig;
import i18nautoupdatemod.core.ModConfig;
import i18nautoupdatemod.core.LivePackActivation;
import i18nautoupdatemod.core.ResourcePack;
import i18nautoupdatemod.core.ResourcePackConverter;
import i18nautoupdatemod.entity.GameAssetDetail;
import i18nautoupdatemod.entity.GameMetaData;
import i18nautoupdatemod.util.AssetUtil;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.ModUtil;
import i18nautoupdatemod.util.Version;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class I18nAutoUpdateMod {
    public static final String MOD_ID = "i18nautoupdatemod";
    public static final String OLD_MOD_ID = "i18nupdatemod";
    public static final String MOD_VERSION = implementationVersion();
    private static final Pattern GAME_VERSION = Pattern.compile("[0-9][0-9A-Za-z._+-]{0,63}");
    private static final String PACK_BASE_NAME = "Minecraft-Mod-Language-Modpack";
    private static final Version MODERN_PACK_ID_VERSION = Version.from("1.13");

    public static final String INITIAL_TIMEOUT_PROPERTY = "i18nautoupdatemod.initialTimeout";
    public static final int DEFAULT_INITIAL_DOWNLOAD_TIMEOUT_SECONDS = ModConfig.DEFAULT_INITIAL_TIMEOUT_SECONDS;

    static final long BETA_UPDATE_GAP_MILLIS = TimeUnit.HOURS.toMillis(6);

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
            Set<String> modIds) {
        if (minecraftVersion == null || !GAME_VERSION.matcher(minecraftVersion).matches()) {
            Log.warning("Invalid Minecraft version: %s", minecraftVersion);
            return CompletableFuture.completedFuture(null);
        }

        Log.info("I18nAutoUpdateMod %s is loaded in %s with %s",
                MOD_VERSION, minecraftVersion, loader);
        Log.debug("Minecraft path: %s", minecraftPath);

        if (isNetEaseMinecraft()) {
            logNetEaseWarning();
            return CompletableFuture.completedFuture(null);
        }

        ModConfig config = ModConfig.load(minecraftPath);
        if (!config.isLanguageAllowed()) {
            Log.info("onlyWhenChineseSystem is enabled and the system locale is %s, skipping",
                    Locale.getDefault());
            return CompletableFuture.completedFuture(null);
        }
        Set<String> knownMods = new HashSet<>(modIds);

        String convertedFileName = I18nConfig.convertedFileName(minecraftVersion);
        Path convertedPackPath = minecraftPath.resolve("resourcepacks").resolve(convertedFileName);
        boolean packAlreadyExists = Files.isRegularFile(convertedPackPath);

        if (packAlreadyExists) {
            registerResourcePack(minecraftPath, minecraftVersion, convertedFileName, config);
        }

        CompletableFuture<Void> future = startAsyncOnce(() -> updateResourcePack(
                minecraftPath, minecraftVersion, loader, convertedPackPath, knownMods, config));

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
                            "Initial resource pack download timed out after %d seconds; continuing in the background.",
                            timeoutSeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.warning("Initial resource pack download interrupted: %s", e);
                } catch (ExecutionException e) {
                    Log.warning("Initial resource pack download failed: %s", e.getCause());
                }
            }
            if (Files.isRegularFile(convertedPackPath)) {
                registerResourcePack(minecraftPath, minecraftVersion, convertedFileName, config);
            } else if (LivePackActivation.supports(minecraftVersion)) {
                String packId = resourcePackId(minecraftVersion, convertedFileName);
                future.thenRunAsync(() -> {
                    if (Files.isRegularFile(convertedPackPath)) {
                        LivePackActivation.schedule(PACK_BASE_NAME, packId, config.forceBottom);
                    }
                }, UPDATE_EXECUTOR);
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
            Path convertedPackPath,
            Set<String> modDomains,
            ModConfig config) {
        try {
            GameMetaData metaData = I18nConfig.getPackFormat(minecraftVersion);
            modDomains.addAll(ModUtil.getModDomainsFromModsFolder(minecraftPath));
            Set<String> excludedMods = config.normalizedExcludeMods();
            excludedMods.add(MOD_ID);
            excludedMods.add(OLD_MOD_ID);
            modDomains.removeIf(domain -> excludedMods.contains(domain.toLowerCase(Locale.ROOT)));

            Path storagePath = prepareStoragePath();
            Path resourcePackDirectory = convertedPackPath.getParent();
            Log.debug("Local storage path: %s", storagePath);

            GameAssetDetail assets = I18nConfig.getAssetDetail(minecraftVersion, loader, config);
            // Beta packs share file names with stable ones, so they need their own cache.
            Path cacheRoot = config.betaPack ? storagePath.resolve("beta") : storagePath;
            long updateGap = config.cacheTtlMillis();
            if (config.betaPack) {
                updateGap = Math.min(BETA_UPDATE_GAP_MILLIS, updateGap);
            }
            List<ResourcePack> languagePacks = new ArrayList<>();
            for (GameAssetDetail.AssetDownloadDetail detail : assets.downloads) {
                ResourcePack languagePack = new ResourcePack(
                        detail.fileName,
                        resourcePackDirectory,
                        cacheRoot.resolve(detail.targetVersion),
                        updateGap);
                languagePack.checkUpdate(() -> AssetUtil.resolveSources(detail.fileName, detail.checksumFileName, config));
                languagePacks.add(languagePack);
            }

            ResourcePackConverter converter = new ResourcePackConverter(languagePacks, convertedPackPath);
            converter.convert(
                    metaData,
                    getResourcePackDescription(assets.downloads, config.betaPack),
                    modDomains);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to update resource pack", e);
        }
    }

    private static void registerResourcePack(
            Path minecraftPath, String minecraftVersion, String convertedFileName, ModConfig config) {
        try {
            GameConfig options = new GameConfig(minecraftPath.resolve("options.txt"));
            boolean changed = options.addResourcePack(
                    PACK_BASE_NAME,
                    resourcePackId(minecraftVersion, convertedFileName),
                    config.forceBottom);
            if (changed) {
                options.writeToFile();
            }
        } catch (IOException | JsonParseException e) {
            Log.warning("Failed to register resource pack: %s", e);
        }
    }

    static String resourcePackId(String minecraftVersion, String convertedFileName) {
        Version version = Version.from(minecraftVersion);
        boolean usesModernPackId = version != null && version.compareTo(MODERN_PACK_ID_VERSION) >= 0;
        return (usesModernPackId ? "file/" : "") + convertedFileName;
    }

    static String getResourcePackDescription(
            List<GameAssetDetail.AssetDownloadDetail> downloads, boolean withBeta) {
        String description = getResourcePackDescription(downloads);
        return withBeta ? description + "\n（Beta 模式：优先使用包含未合并 PR 的翻译）" : description;
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

    public static Path prepareStoragePath() throws IOException {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path platformBase = getPlatformStorageBase(userHome);
        Path homeNew = userHome.resolve("." + MOD_ID);
        Path homeOld = userHome.resolve("." + OLD_MOD_ID);
        Path platformNew = platformBase.resolve("." + MOD_ID);
        Path platformOld = platformBase.resolve("." + OLD_MOD_ID);

        Path target = Files.exists(homeNew) || Files.exists(homeOld) ? homeNew : platformNew;
        FileUtil.migrateDirectory(homeOld, target);
        if (!homeOld.equals(platformOld)) {
            FileUtil.migrateDirectory(platformOld, target);
        }
        Files.createDirectories(target);
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
            Class.forName("com.netease.mc.mod.network.common.Library", false, I18nAutoUpdateMod.class.getClassLoader());
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
