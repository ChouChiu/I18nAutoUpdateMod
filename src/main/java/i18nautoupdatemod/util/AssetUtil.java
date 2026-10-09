package i18nautoupdatemod.util;

import i18nautoupdatemod.core.ModConfig;
import i18nautoupdatemod.entity.AssetSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class AssetUtil {
    static final String GITHUB_ASSET_ROOT =
            "https://github.com/CFPAOrg/Minecraft-Mod-Language-Package/releases/download/autobuild/";
    static final String CFPA_ASSET_ROOT = "http://downloader1.meitangdehulu.com:22943/";
    static final String COMMUNITY_MIRROR_ROOT = "http://8.137.167.65:64684/";
    static final String AUTOMERGE_ASSET_ROOT =
            "https://github.com/ChouChiu/CFPA-AutoMerge/releases/download/automerge/";
    static final String AUTOMERGE_SOURCE_NAME = "AutoMerge";

    private static final int CONNECT_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);
    private static final int READ_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(30);
    private static final List<SourceRoot> SOURCE_ROOTS = createSourceRoots();

    private AssetUtil() {
    }

    private static List<SourceRoot> createSourceRoots() {
        List<SourceRoot> roots = new ArrayList<>();
        roots.add(new SourceRoot(ModConfig.SOURCE_GITHUB, GITHUB_ASSET_ROOT, false));
        roots.add(new SourceRoot(ModConfig.SOURCE_CFPA, CFPA_ASSET_ROOT, true));
        roots.add(new SourceRoot(ModConfig.SOURCE_COMMUNITY, COMMUNITY_MIRROR_ROOT, true));
        return roots;
    }

    public static List<AssetSource> resolveSources(String fileName, String checksumFileName) {
        return resolveSources(fileName, checksumFileName, ModConfig.defaults());
    }

    public static List<AssetSource> resolveSources(
            String fileName, String checksumFileName, ModConfig config) {
        List<AssetSource> sources = orderSources(fileName, checksumFileName,
                config.normalizedDefaultSource(),
                config.normalizedMirrorPriority(),
                LocationDetectUtil::isMainlandChina,
                SOURCE_ROOTS);
        return config.betaPack ? withBetaSource(sources, fileName, checksumFileName) : sources;
    }

    /**
     * Puts the AutoMerge release, built from every open pull request, in front of the stable
     * sources, which stay as the fallback.
     */
    static List<AssetSource> withBetaSource(
            List<AssetSource> sources, String fileName, String checksumFileName) {
        List<AssetSource> result = new ArrayList<>();
        result.addAll(toAssetSources(
                Collections.singletonList(new SourceRoot(AUTOMERGE_SOURCE_NAME, AUTOMERGE_ASSET_ROOT, false)),
                fileName, checksumFileName));
        result.addAll(sources);
        return result;
    }

    static List<AssetSource> orderSources(
            String fileName,
            String checksumFileName,
            boolean mainlandChina,
            List<SourceRoot> sourceRoots
    ) {
        return orderSources(fileName, checksumFileName, null, null,
                () -> mainlandChina, sourceRoots);
    }

    /**
     * @param defaultSource source tried first, or {@code null} to pick one by location and probing
     * @param priority      fallback order of the remaining sources, or {@code null} to keep the
     *                      built-in order
     */
    static List<AssetSource> orderSources(
            String fileName,
            String checksumFileName,
            String defaultSource,
            List<String> priority,
            BooleanSupplier mainlandChina,
            List<SourceRoot> sourceRoots
    ) {
        if (defaultSource != null) {
            SourceRoot preferred = findRoot(sourceRoots, defaultSource);
            if (preferred != null) {
                Log.info("Configured default source for %s: %s", fileName, preferred.name);
                List<SourceRoot> ordered = new ArrayList<>();
                ordered.add(preferred);
                ordered.addAll(sortByPriority(without(sourceRoots, preferred), priority));
                return toAssetSources(ordered, fileName, checksumFileName);
            }
            Log.warning("Configured default source %s is unavailable, using auto", defaultSource);
        }

        if (!mainlandChina.getAsBoolean()) {
            Log.info("Outside mainland China: preferring GitHub for %s", fileName);
            if (priority == null || sourceRoots.isEmpty()) {
                return toAssetSources(sourceRoots, fileName, checksumFileName);
            }
            SourceRoot preferred = sourceRoots.stream()
                    .filter(root -> !root.domestic)
                    .findFirst()
                    .orElse(sourceRoots.get(0));
            List<SourceRoot> ordered = new ArrayList<>();
            ordered.add(preferred);
            ordered.addAll(sortByPriority(without(sourceRoots, preferred), priority));
            return toAssetSources(ordered, fileName, checksumFileName);
        }

        List<SourceRoot> domestic = new ArrayList<>();
        List<SourceRoot> fallback = new ArrayList<>();
        for (SourceRoot root : sourceRoots) {
            (root.domestic ? domestic : fallback).add(root);
        }

        Log.info("Inside mainland China: probing domestic sources for %s", fileName);
        List<ProbeResult> results = probe(checksumFileName, domestic);
        results.sort(Comparator.comparingLong(result -> result.elapsedNanos));

        List<SourceRoot> reachable = new ArrayList<>();
        for (ProbeResult result : results) {
            if (result.reachable) {
                reachable.add(result.root);
            }
        }

        List<SourceRoot> ordered = new ArrayList<>();
        if (priority == null) {
            ordered.addAll(reachable);
            for (SourceRoot root : domestic) {
                if (!ordered.contains(root)) {
                    ordered.add(root);
                }
            }
            ordered.addAll(fallback);
        } else if (!sourceRoots.isEmpty()) {
            SourceRoot preferred = reachable.isEmpty()
                    ? sortByPriority(sourceRoots, priority).get(0)
                    : reachable.get(0);
            ordered.add(preferred);
            List<SourceRoot> rest = sortByPriority(without(sourceRoots, preferred), priority);
            // Domestic sources that failed the probe are kept only as a last resort.
            for (SourceRoot root : rest) {
                if (!domestic.contains(root) || reachable.contains(root)) {
                    ordered.add(root);
                }
            }
            for (SourceRoot root : rest) {
                if (!ordered.contains(root)) {
                    ordered.add(root);
                }
            }
        }

        if (!ordered.isEmpty()) {
            Log.info("Preferred source for %s: %s", fileName, ordered.get(0).name);
        }
        return toAssetSources(ordered, fileName, checksumFileName);
    }

    private static SourceRoot findRoot(List<SourceRoot> roots, String name) {
        for (SourceRoot root : roots) {
            if (root.name.equalsIgnoreCase(name)) {
                return root;
            }
        }
        return null;
    }

    private static List<SourceRoot> without(List<SourceRoot> roots, SourceRoot excluded) {
        List<SourceRoot> result = new ArrayList<>(roots);
        result.remove(excluded);
        return result;
    }

    private static List<SourceRoot> sortByPriority(List<SourceRoot> roots, List<String> priority) {
        List<SourceRoot> result = new ArrayList<>(roots);
        if (priority == null) {
            return result;
        }
        result.sort(Comparator.comparingInt(root -> {
            for (int i = 0; i < priority.size(); i++) {
                if (priority.get(i).equalsIgnoreCase(root.name)) {
                    return i;
                }
            }
            return priority.size();
        }));
        return result;
    }

    private static List<ProbeResult> probe(String checksumFileName, List<SourceRoot> roots) {
        if (roots.isEmpty()) {
            return new ArrayList<>();
        }
        ExecutorService executor = Executors.newFixedThreadPool(roots.size(), runnable -> {
            Thread thread = new Thread(runnable, "i18nautoupdatemod-source-probe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<ProbeResult>> futures = new ArrayList<>();
            for (SourceRoot root : roots) {
                futures.add(executor.submit((Callable<ProbeResult>) () -> {
                    long start = System.nanoTime();
                    boolean reachable;
                    try {
                        String checksum = getString(root.baseUrl + checksumFileName,
                                CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(5)).trim();
                        reachable = checksum.matches("(?i)[0-9a-f]{32}");
                    } catch (Exception e) {
                        Log.debug("Source probe failed for %s: %s", root.name, e);
                        reachable = false;
                    }
                    return new ProbeResult(root, reachable, System.nanoTime() - start);
                }));
            }

            List<ProbeResult> results = new ArrayList<>();
            for (Future<ProbeResult> future : futures) {
                try {
                    results.add(future.get(7, TimeUnit.SECONDS));
                } catch (Exception e) {
                    Log.debug("Source probe task failed: %s", e);
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<AssetSource> toAssetSources(
            List<SourceRoot> roots, String fileName, String checksumFileName) {
        List<AssetSource> sources = new ArrayList<>();
        for (SourceRoot root : roots) {
            sources.add(new AssetSource(
                    root.name,
                    root.baseUrl + fileName,
                    root.baseUrl + checksumFileName
            ));
        }
        return sources;
    }

    public static void download(String url, Path localFile) throws IOException {
        Log.info("Downloading: %s -> %s", url, localFile);
        Files.createDirectories(localFile.getParent());
        HttpURLConnection connection = openConnection(url, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + " while downloading " + url);
            }
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(localFile,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
        Log.debug("Downloaded: %s -> %s", url, localFile);
    }

    public static String getString(String url) throws IOException {
        return getString(url, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    static String getString(String url, int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection connection = openConnection(url, connectTimeout, readTimeout);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + " while reading " + url);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(
            String url, int connectTimeout, int readTimeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "I18nAutoUpdateMod/1.0");
        return connection;
    }

    static final class SourceRoot {
        final String name;
        final String baseUrl;
        final boolean domestic;

        SourceRoot(String name, String baseUrl, boolean domestic) {
            this.name = name;
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            this.domestic = domestic;
        }
    }

    private static final class ProbeResult {
        final SourceRoot root;
        final boolean reachable;
        final long elapsedNanos;

        private ProbeResult(SourceRoot root, boolean reachable, long elapsedNanos) {
            this.root = root;
            this.reachable = reachable;
            this.elapsedNanos = elapsedNanos;
        }
    }
}
