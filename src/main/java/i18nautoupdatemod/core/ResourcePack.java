package i18nautoupdatemod.core;

import i18nautoupdatemod.entity.AssetSource;
import i18nautoupdatemod.util.AssetUtil;
import i18nautoupdatemod.util.DigestUtil;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ResourcePack {
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(1);

    private final String filename;
    private final Path cacheFile;
    private final Path downloadFile;

    public ResourcePack(String filename, Path resourcePackDirectory, Path cacheDirectory)
            throws IOException {
        this.filename = filename;
        this.cacheFile = cacheDirectory.resolve(filename);
        this.downloadFile = cacheDirectory.resolve(filename + ".part");
        FileUtil.ensureDirectory(cacheDirectory);
        FileUtil.seedCache(resourcePackDirectory.resolve(filename), cacheFile);
    }

    public void checkUpdate(List<AssetSource> sources) throws IOException {
        if (wasUpdatedRecently()) {
            Log.debug("Local file %s has been updated recently.", cacheFile);
            return;
        }

        Exception lastFailure = null;
        for (AssetSource source : sources) {
            try {
                String remoteMd5 = getRemoteMd5(source);
                if (Files.exists(cacheFile) && matches(cacheFile, remoteMd5)) {
                    Log.debug("Already up to date from %s: %s", source.name, cacheFile);
                    return;
                }

                Files.deleteIfExists(downloadFile);
                AssetUtil.download(source.fileUrl, downloadFile);
                if (!matches(downloadFile, remoteMd5)) {
                    throw new IOException("Downloaded file checksum mismatch");
                }
                FileUtil.atomicReplace(downloadFile, cacheFile);
                Log.info("Updated %s from %s", filename, source.name);
                return;
            } catch (Exception e) {
                lastFailure = e;
                Log.warning("Failed to update %s from %s: %s", filename, source.name, e);
                try {
                    Files.deleteIfExists(downloadFile);
                } catch (IOException cleanupError) {
                    Log.debug("Failed to clean %s: %s", downloadFile, cleanupError);
                }
            }
        }

        if (Files.exists(cacheFile)) {
            Log.warning("All sources failed for %s; using cached resource pack", filename);
            return;
        }
        FileNotFoundException failure =
                new FileNotFoundException("No resource pack available for " + filename);
        if (lastFailure != null) {
            failure.initCause(lastFailure);
        }
        throw failure;
    }

    private boolean wasUpdatedRecently() throws IOException {
        return Files.exists(cacheFile)
                && Files.getLastModifiedTime(cacheFile).toMillis()
                > System.currentTimeMillis() - UPDATE_TIME_GAP;
    }

    private String getRemoteMd5(AssetSource source) throws IOException {
        String value = AssetUtil.getString(source.checksumUrl).trim();
        if (!value.matches("(?i)[0-9a-f]{32}")) {
            throw new IOException("Invalid MD5 response from " + source.name);
        }
        return value;
    }

    private boolean matches(Path file, String remoteMd5)
            throws IOException, NoSuchAlgorithmException {
        String localMd5 = DigestUtil.md5Hex(file);
        Log.debug("%s md5: %s, remote md5: %s", file, localMd5, remoteMd5);
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    public Path getCacheFile() {
        return cacheFile;
    }

    public String getFilename() {
        return filename;
    }
}
