package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import i18nautoupdatemod.entity.GameMetaData;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Path> sourcePaths;
    private final Path resourcePackFile;

    public ResourcePackConverter(List<ResourcePack> resourcePacks, Path resourcePackFile) {
        this.sourcePaths = resourcePacks.stream()
                .map(ResourcePack::getCacheFile)
                .collect(Collectors.toList());
        this.resourcePackFile = resourcePackFile;
    }

    public void convert(GameMetaData metaData, String description, Set<String> modDomains)
            throws IOException {
        FileUtil.writeAtomically(resourcePackFile, temporary -> {
            Set<String> written = new HashSet<>();
            long remaining = PackArchive.MAX_EXPANDED_BYTES;
            try (ZipOutputStream output = new ZipOutputStream(
                    Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                for (Path sourcePath : sourcePaths) {
                    Log.info("Converting: %s", sourcePath);
                    try (PackArchive archive = PackArchive.open(sourcePath)) {
                        for (Enumeration<? extends ZipEntry> entries = archive.entries(); entries.hasMoreElements(); ) {
                            ZipEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!shouldInclude(name, modDomains) || !written.add(name)) {
                                continue;
                            }

                            output.putNextEntry(new ZipEntry(name));
                            if (PackArchive.METADATA.equals(name)) {
                                output.write(convertPackMeta(archive.metadata(), metaData, description));
                            } else if (!entry.isDirectory()) {
                                remaining -= archive.copyEntry(entry, output, remaining);
                            }
                            output.closeEntry();
                        }
                    }
                }
            }
        });
        Log.info("Converted resource pack: %s -> %s", sourcePaths, resourcePackFile);
    }

    private static boolean shouldInclude(String name, Set<String> modDomains) {
        String[] parts = name.split("/", 4);
        if (parts.length < 2 || parts[1].isEmpty() || !"assets".equals(parts[0])) {
            return true;
        }
        // Keep Minecraft baseline fixes such as fonts, excluding its language files by default.
        return modDomains.contains(parts[1])
                || ("minecraft".equals(parts[1]) && (parts.length < 3 || !"lang".equals(parts[2])));
    }

    static byte[] convertPackMeta(InputStream input, GameMetaData metaData, String description) throws IOException {
        return convertPackMeta(PackArchive.readMetadata(input), metaData, description);
    }

    private static byte[] convertPackMeta(JsonObject root, GameMetaData metaData, String description) {
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
        return toAsciiJson(GSON.toJson(root));
    }

    /**
     * Older clients read pack.mcmeta with the platform charset, so non-ASCII
     * characters are written as JSON unicode escapes.
     */
    static byte[] toAsciiJson(String json) {
        StringBuilder builder = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (character <= 0x7F) {
                builder.append(character);
            } else {
                builder.append(String.format("\\u%04x", (int) character));
            }
        }
        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
