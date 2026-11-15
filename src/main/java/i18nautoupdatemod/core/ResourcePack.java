package i18nautoupdatemod.core;

import i18nautoupdatemod.entity.AssetSource;
import i18nautoupdatemod.util.AssetUtil;
import i18nautoupdatemod.util.DigestUtil;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.function.Supplier;

public final class ResourcePack {

    private final String filename;
    private final Path cacheFile;
    private final long updateGapMillis;

    public ResourcePack(String filename, Path resourcePackDirectory, Path cacheDirectory)
            throws IOException {
        this(filename, resourcePackDirectory, cacheDirectory, ModConfig.defaults().cacheTtlMillis());
    }

    public ResourcePack(
            String filename, Path resourcePackDirectory, Path cacheDirectory, long updateGapMillis)
            throws IOException {
        this.filename = filename;
        this.updateGapMillis = updateGapMillis;
        this.cacheFile = cacheDirectory.resolve(filename);
        FileUtil.seedCache(resourcePackDirectory.resolve(filename), cacheFile);
    }

    public void checkUpdate(Supplier<List<AssetSource>> sources) throws IOException {
        boolean usableCache = hasUsableCache();
        long now = System.currentTimeMillis();
        if (usableCache && updateGapMillis > 0) {
            long modified = Files.getLastModifiedTime(cacheFile).toMillis();
            if (modified <= now && now - modified < updateGapMillis) {
                return;
            }
        }

        String localMd5 = usableCache ? DigestUtil.md5Hex(cacheFile) : null;
        IOException lastFailure = null;
        for (AssetSource source : sources.get()) {
            try {
                String remoteMd5 = AssetUtil.readChecksum(source.checksumUrl);
                if (remoteMd5.equalsIgnoreCase(localMd5)) {
                    Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(System.currentTimeMillis()));
                    Log.debug("Already up to date from %s: %s", source.name, cacheFile);
                } else {
                    FileUtil.writeAtomically(cacheFile, temporary -> {
                        AssetUtil.download(source.fileUrl, temporary);
                        if (!remoteMd5.equalsIgnoreCase(DigestUtil.md5Hex(temporary))) {
                            throw new IOException("Downloaded file checksum mismatch");
                        }
                        PackArchive.validateDownload(temporary);
                    });
                    Log.info("Updated %s from %s", filename, source.name);
                }
                return;
            } catch (IOException e) {
                lastFailure = e;
                Log.warning("Failed to update %s from %s: %s", filename, source.name, e);
            }
        }

        if (usableCache) {
            Log.warning("All sources failed for %s; using cached resource pack", filename);
            return;
        }
        throw new IOException("No usable resource pack available for " + filename, lastFailure);
    }

    private boolean hasUsableCache() {
        if (!Files.isRegularFile(cacheFile)) {
            return false;
        }
        try (PackArchive ignored = PackArchive.open(cacheFile)) {
            return true;
        } catch (IOException e) {
            Log.warning("Invalid cached resource pack %s: %s", cacheFile, e);
            return false;
        }
    }

    public Path getCacheFile() {
        return cacheFile;
    }
}
