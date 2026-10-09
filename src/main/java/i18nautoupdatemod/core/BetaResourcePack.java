package i18nautoupdatemod.core;

import i18nautoupdatemod.util.AssetUtil;
import i18nautoupdatemod.util.DigestUtil;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview packs built by CFPA Project Hex from pull requests that are not merged yet.
 * File names end with the first six hex digits of the file's SHA-256, which doubles as
 * the checksum since the index does not publish one.
 */
public class BetaResourcePack {
    public static final String BETA_ROOT = "https://cfpa.cyan.cafe/project-hex/";

    private static final Pattern INDEX_ENTRY = Pattern.compile(
            "href=\"(?:\\./)?(Minecraft-Mod-Language-Package-([0-9.]+)(-fabric)?-([0-9A-Fa-f]{6})\\.zip)\"");

    private final String baseUrl;
    private final Path cacheRoot;
    private Map<String, Entry> index;
    private boolean indexFetched;

    public BetaResourcePack(Path cacheRoot) {
        this(BETA_ROOT, cacheRoot);
    }

    BetaResourcePack(String baseUrl, Path cacheRoot) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.cacheRoot = cacheRoot;
    }

    /**
     * @return the local beta pack for the given CFPA pack version, preferring the loader's own
     * variant and falling back to Forge, or {@code null} when there is none. Never throws, so a
     * beta failure cannot break the stable pack update.
     */
    public Path resolve(String targetVersion, String loader) {
        for (boolean fabric : variantOrder(loader)) {
            Path pack = resolveVariant(targetVersion, fabric);
            if (pack != null) {
                return pack;
            }
        }
        Log.info("No beta pack available for %s", targetVersion);
        return null;
    }

    /**
     * @return every available variant of the given CFPA pack version, the loader's own first
     */
    public List<Path> resolveAll(String targetVersion, String loader) {
        List<Path> packs = new ArrayList<>();
        for (boolean fabric : variantOrder(loader)) {
            Path pack = resolveVariant(targetVersion, fabric);
            if (pack != null) {
                packs.add(pack);
            }
        }
        if (packs.isEmpty()) {
            Log.info("No beta pack available for %s", targetVersion);
        }
        return packs;
    }

    private static boolean[] variantOrder(String loader) {
        boolean fabric = "Fabric".equals(I18nConfig.normalizeLoader(loader));
        return fabric ? new boolean[]{true, false} : new boolean[]{false, true};
    }

    private Path resolveVariant(String targetVersion, boolean fabric) {
        Path directory = cacheRoot.resolve(targetVersion).resolve(fabric ? "fabric" : "forge");
        try {
            Map<String, Entry> entries = getIndex();
            if (entries == null) {
                Path cached = findCached(directory);
                if (cached != null) {
                    Log.warning("Beta index unavailable; using cached beta pack %s", cached);
                }
                return cached;
            }

            Entry entry = entries.get(key(targetVersion, fabric));
            if (entry == null) {
                return null;
            }

            Path target = directory.resolve(entry.fileName);
            if (!Files.exists(target) || !matches(target, entry.hash)) {
                download(entry, target);
            }
            removeOthers(directory, target);
            Log.info("Using beta pack %s", entry.fileName);
            return target;
        } catch (Exception e) {
            Log.warning("Failed to update beta pack for %s%s: %s",
                    targetVersion, fabric ? " (Fabric)" : "", e);
            try {
                return findCached(directory);
            } catch (IOException cacheError) {
                Log.debug("Failed to read beta cache %s: %s", directory, cacheError);
                return null;
            }
        }
    }

    private Map<String, Entry> getIndex() {
        if (!indexFetched) {
            indexFetched = true;
            try {
                index = parseIndex(AssetUtil.getString(baseUrl));
            } catch (Exception e) {
                Log.warning("Failed to fetch beta index %s: %s", baseUrl, e);
                index = null;
            }
        }
        return index;
    }

    static Map<String, Entry> parseIndex(String html) {
        Map<String, Entry> entries = new HashMap<>();
        Matcher matcher = INDEX_ENTRY.matcher(html);
        while (matcher.find()) {
            Entry entry = new Entry(matcher.group(1), matcher.group(4).toUpperCase(Locale.ROOT));
            entries.put(key(matcher.group(2), matcher.group(3) != null), entry);
        }
        return entries;
    }

    private static String key(String version, boolean fabric) {
        return version + (fabric ? "-fabric" : "");
    }

    private void download(Entry entry, Path target) throws Exception {
        Path part = target.resolveSibling(entry.fileName + ".part");
        try {
            Files.deleteIfExists(part);
            AssetUtil.download(baseUrl + entry.fileName, part);
            if (!matches(part, entry.hash)) {
                throw new IOException("Downloaded beta pack checksum mismatch");
            }
            FileUtil.atomicReplace(part, target);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static boolean matches(Path file, String hash) throws Exception {
        return DigestUtil.sha256Hex(file).startsWith(hash);
    }

    private static Path findCached(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path file : stream) {
                return file;
            }
        }
        return null;
    }

    private static void removeOthers(Path directory, Path keep) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!file.equals(keep)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    static final class Entry {
        final String fileName;
        final String hash;

        Entry(String fileName, String hash) {
            this.fileName = fileName;
            this.hash = hash;
        }
    }
}
