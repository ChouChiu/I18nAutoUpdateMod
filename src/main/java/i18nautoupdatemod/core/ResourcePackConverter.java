package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern JSON_LANG = Pattern.compile("(?i)assets/[^/]+/lang/[^/]+\\.json");
    private static final Pattern LEGACY_LANG = Pattern.compile("(?i)assets/[^/]+/lang/[^/]+\\.lang");

    private final List<Path> sourcePaths;
    private final List<Path> overlayPaths;
    private final Path cacheFile;
    private final Path temporaryFile;
    private final Path resourcePackFile;

    public ResourcePackConverter(
            List<ResourcePack> resourcePacks,
            String filename,
            Path cacheDirectory,
            Path resourcePackDirectory
    ) throws Exception {
        this(resourcePacks, Collections.emptyList(), filename, cacheDirectory, resourcePackDirectory);
    }

    /**
     * @param overlayPaths packs whose translations are merged on top of {@code resourcePacks}
     *                     key by key, such as beta packs
     */
    public ResourcePackConverter(
            List<ResourcePack> resourcePacks,
            List<Path> overlayPaths,
            String filename,
            Path cacheDirectory,
            Path resourcePackDirectory
    ) throws Exception {
        this.overlayPaths = new ArrayList<>(overlayPaths);
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
            Overlay overlay = readOverlay(modDomains);
            for (Path sourcePath : sourcePaths) {
                Log.info("Converting: %s", sourcePath);
                try (ZipFile zipFile = new ZipFile(sourcePath.toFile(), StandardCharsets.UTF_8)) {
                    for (Enumeration<? extends ZipEntry> entries = zipFile.entries();
                         entries.hasMoreElements(); ) {
                        ZipEntry sourceEntry = entries.nextElement();
                        String name = sourceEntry.getName();
                        if (isFilteredOut(name, modDomains)) {
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
                                } else if (overlay.contains(name)) {
                                    output.write(overlay.apply(name, IOUtils.toByteArray(input)));
                                } else {
                                    IOUtils.copy(input, output);
                                }
                            }
                        }
                        output.closeEntry();
                    }
                }
            }
            for (String name : overlay.names()) {
                if (fileList.add(name)) {
                    output.putNextEntry(new ZipEntry(name));
                    output.write(overlay.apply(name, null));
                    output.closeEntry();
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

    private static boolean isFilteredOut(String name, Set<String> modDomains) {
        String[] parts = name.split("/");
        return parts.length >= 2
                && "assets".equals(parts[0])
                && !modDomains.contains(parts[1]);
    }

    private Overlay readOverlay(Set<String> modDomains) throws Exception {
        Overlay overlay = new Overlay();
        for (Path overlayPath : overlayPaths) {
            Log.info("Reading overlay: %s", overlayPath);
            try (ZipFile zipFile = new ZipFile(overlayPath.toFile(), StandardCharsets.UTF_8)) {
                for (Enumeration<? extends ZipEntry> entries = zipFile.entries();
                     entries.hasMoreElements(); ) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory()
                            || "pack.mcmeta".equalsIgnoreCase(name)
                            || isFilteredOut(name, modDomains)) {
                        continue;
                    }
                    byte[] content;
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        content = IOUtils.toByteArray(input);
                    }
                    overlay.add(name, content);
                }
            }
        }
        return overlay;
    }

    /**
     * Translations layered on top of the stable packs. Earlier overlays win, matching the
     * first-wins order of the stable packs, and empty files never replace stable content.
     */
    static final class Overlay {
        private final Map<String, JsonObject> jsonLang = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> legacyLang = new LinkedHashMap<>();
        private final Map<String, byte[]> files = new LinkedHashMap<>();

        void add(String name, byte[] content) {
            if (JSON_LANG.matcher(name).matches()) {
                JsonObject object = parseJsonObject(content);
                if (object == null || object.size() == 0) {
                    return;
                }
                JsonObject merged = jsonLang.computeIfAbsent(name, it -> new JsonObject());
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (!merged.has(entry.getKey())) {
                        merged.add(entry.getKey(), entry.getValue());
                    }
                }
            } else if (LEGACY_LANG.matcher(name).matches()) {
                Map<String, String> values = parseLegacyLang(content);
                if (values.isEmpty()) {
                    return;
                }
                Map<String, String> merged = legacyLang.computeIfAbsent(name, it -> new LinkedHashMap<>());
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    merged.putIfAbsent(entry.getKey(), entry.getValue());
                }
            } else if (content.length > 0) {
                files.putIfAbsent(name, content);
            }
        }

        boolean contains(String name) {
            return jsonLang.containsKey(name) || legacyLang.containsKey(name) || files.containsKey(name);
        }

        Set<String> names() {
            Set<String> names = new LinkedHashSet<>(jsonLang.keySet());
            names.addAll(legacyLang.keySet());
            names.addAll(files.keySet());
            return names;
        }

        /**
         * @param base stable content of the same entry, or {@code null} when the overlay adds it
         */
        byte[] apply(String name, byte[] base) {
            if (jsonLang.containsKey(name)) {
                JsonObject result = base == null ? null : parseJsonObject(base);
                if (result == null) {
                    result = new JsonObject();
                }
                for (Map.Entry<String, JsonElement> entry : jsonLang.get(name).entrySet()) {
                    result.add(entry.getKey(), entry.getValue());
                }
                return COMPACT_GSON.toJson(result).getBytes(StandardCharsets.UTF_8);
            }
            if (legacyLang.containsKey(name)) {
                return mergeLegacyLang(base, legacyLang.get(name));
            }
            return files.get(name);
        }

        private static JsonObject parseJsonObject(byte[] content) {
            try {
                JsonElement element = JsonParser.parseString(stripBom(new String(content, StandardCharsets.UTF_8)));
                return element.isJsonObject() ? element.getAsJsonObject() : null;
            } catch (Exception e) {
                Log.debug("Invalid language JSON skipped: %s", e);
                return null;
            }
        }

        private static Map<String, String> parseLegacyLang(byte[] content) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String line : stripBom(new String(content, StandardCharsets.UTF_8)).split("\\r?\\n")) {
                String key = legacyKey(line);
                if (key != null) {
                    values.putIfAbsent(key, line.substring(line.indexOf('=') + 1));
                }
            }
            return values;
        }

        private static byte[] mergeLegacyLang(byte[] base, Map<String, String> overlay) {
            Map<String, String> pending = new LinkedHashMap<>(overlay);
            StringBuilder result = new StringBuilder();
            if (base != null) {
                for (String line : stripBom(new String(base, StandardCharsets.UTF_8)).split("\\r?\\n")) {
                    String key = legacyKey(line);
                    if (key != null && pending.containsKey(key)) {
                        line = key + "=" + pending.remove(key);
                    }
                    result.append(line).append('\n');
                }
            }
            for (Map.Entry<String, String> entry : pending.entrySet()) {
                result.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            return result.toString().getBytes(StandardCharsets.UTF_8);
        }

        private static String legacyKey(String line) {
            String trimmed = line.trim();
            int separator = line.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || separator <= 0) {
                return null;
            }
            return line.substring(0, separator);
        }

        private static String stripBom(String text) {
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
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
