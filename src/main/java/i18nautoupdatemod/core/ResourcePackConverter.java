package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import i18nautoupdatemod.entity.GameMetaData;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Path> sourcePaths;
    private final Path cacheFile;
    private final Path temporaryFile;
    private final Path resourcePackFile;

    public ResourcePackConverter(
            List<ResourcePack> resourcePacks,
            String filename,
            Path cacheDirectory,
            Path resourcePackDirectory
    ) throws Exception {
        this.sourcePaths = resourcePacks.stream()
                .map(ResourcePack::getCacheFile)
                .collect(Collectors.toList());
        FileUtil.ensureDirectory(cacheDirectory);
        FileUtil.ensureDirectory(resourcePackDirectory);
        this.cacheFile = cacheDirectory.resolve(filename);
        this.temporaryFile = cacheDirectory.resolve(filename + ".part");
        this.resourcePackFile = resourcePackDirectory.resolve(filename);
    }

    public void convert(GameMetaData metaData, String description, Set<String> modDomains)
            throws Exception {
        Set<String> fileList = new HashSet<>();
        Files.deleteIfExists(temporaryFile);
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(temporaryFile), StandardCharsets.UTF_8)) {
            for (Path sourcePath : sourcePaths) {
                Log.info("Converting: %s", sourcePath);
                try (ZipFile zipFile = new ZipFile(sourcePath.toFile(), StandardCharsets.UTF_8)) {
                    for (Enumeration<? extends ZipEntry> entries = zipFile.entries();
                         entries.hasMoreElements(); ) {
                        ZipEntry sourceEntry = entries.nextElement();
                        String name = sourceEntry.getName();
                        String[] parts = name.split("/");
                        if (parts.length >= 2
                                && "assets".equals(parts[0])
                                && !modDomains.contains(parts[1])) {
                            continue;
                        }
                        if (!fileList.add(name)) {
                            continue;
                        }

                        output.putNextEntry(new ZipEntry(name));
                        if (!sourceEntry.isDirectory()) {
                            try (InputStream input = zipFile.getInputStream(sourceEntry)) {
                                if ("pack.mcmeta".equalsIgnoreCase(name)) {
                                    output.write(convertPackMeta(input, metaData, description));
                                } else {
                                    IOUtils.copy(input, output);
                                }
                            }
                        }
                        output.closeEntry();
                    }
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(temporaryFile);
            throw new Exception(
                    String.format("Error converting %s to %s: %s",
                            sourcePaths, temporaryFile, e), e);
        }

        FileUtil.atomicReplace(temporaryFile, cacheFile);
        FileUtil.copyAtomically(cacheFile, resourcePackFile);
        Log.info("Converted resource pack: %s -> %s", sourcePaths, resourcePackFile);
    }

    static byte[] convertPackMeta(
            InputStream input, GameMetaData metaData, String description) {
        JsonObject root = JsonParser.parseReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject pack;
        if (root.has("pack") && root.get("pack").isJsonObject()) {
            pack = root.getAsJsonObject("pack");
        } else {
            pack = new JsonObject();
            root.add("pack", pack);
        }

        pack.addProperty("description", description);
        pack.remove("supported_formats");
        if (metaData.useNewFormat()) {
            pack.remove("pack_format");
            pack.addProperty("min_format", metaData.minFormat);
            pack.addProperty("max_format", metaData.maxFormat);
        } else {
            pack.addProperty("pack_format", metaData.packFormat);
            pack.remove("min_format");
            pack.remove("max_format");
        }
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }
}
