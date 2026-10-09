package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ModConfig {
    public static final String FILE_NAME = "i18nautoupdatemod.json";
    public static final String SOURCE_AUTO = "auto";
    public static final String SOURCE_GITHUB = "GitHub";
    public static final String SOURCE_CFPA = "CFPA";
    public static final String SOURCE_COMMUNITY = "Community Mirror";
    public static final List<String> KNOWN_SOURCES =
            Arrays.asList(SOURCE_GITHUB, SOURCE_CFPA, SOURCE_COMMUNITY);
    public static final int DEFAULT_INITIAL_TIMEOUT_SECONDS = 10;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean forceBottom = true;
    public String defaultSource = SOURCE_AUTO;
    public List<String> mirrorPriority = new ArrayList<>(Arrays.asList("CFPA", "Community", "GitHub"));
    public boolean betaPack = false;
    public boolean mergeLoaders = false;
    public int initialTimeout = DEFAULT_INITIAL_TIMEOUT_SECONDS;

    public static ModConfig defaults() {
        return new ModConfig();
    }

    public static ModConfig load(Path minecraftPath) {
        Path file = minecraftPath.resolve("config").resolve(FILE_NAME);
        ModConfig config = defaults();
        try {
            if (Files.exists(file)) {
                JsonObject stored = JsonParser.parseString(
                        new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
                ModConfig parsed = GSON.fromJson(stored, ModConfig.class);
                if (parsed != null) {
                    config = parsed;
                }
                if (config.mirrorPriority == null) {
                    config.mirrorPriority = defaults().mirrorPriority;
                }
                if (config.defaultSource == null) {
                    config.defaultSource = SOURCE_AUTO;
                }
                if (stored.keySet().containsAll(GSON.toJsonTree(config).getAsJsonObject().keySet())) {
                    return config;
                }
            }
        } catch (Exception e) {
            Log.warning("Failed to read %s, using default settings: %s", file, e);
            return defaults();
        }

        try {
            config.write(file);
        } catch (Exception e) {
            Log.warning("Failed to write %s: %s", file, e);
        }
        return config;
    }

    void write(Path file) throws Exception {
        FileUtil.ensureDirectory(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".part");
        try {
            Files.write(temporary, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
            FileUtil.atomicReplace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * @return a known source name, or {@code null} for automatic selection
     */
    public String normalizedDefaultSource() {
        if (defaultSource == null || SOURCE_AUTO.equalsIgnoreCase(defaultSource.trim())) {
            return null;
        }
        String source = normalizeSource(defaultSource);
        if (source == null) {
            Log.warning("Unknown defaultSource '%s', using auto", defaultSource);
        }
        return source;
    }

    public List<String> normalizedMirrorPriority() {
        List<String> result = new ArrayList<>();
        if (mirrorPriority != null) {
            for (String name : mirrorPriority) {
                String source = normalizeSource(name);
                if (source == null) {
                    Log.warning("Unknown mirrorPriority entry '%s' ignored", name);
                } else if (!result.contains(source)) {
                    result.add(source);
                }
            }
        }
        for (String source : KNOWN_SOURCES) {
            if (!result.contains(source)) {
                result.add(source);
            }
        }
        return result;
    }

    static String normalizeSource(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "github":
                return SOURCE_GITHUB;
            case "cfpa":
                return SOURCE_CFPA;
            case "community":
            case "community mirror":
                return SOURCE_COMMUNITY;
            default:
                return null;
        }
    }
}
