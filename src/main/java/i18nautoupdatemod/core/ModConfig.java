package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ModConfig {
    public static final String FILE_NAME = "i18nautoupdatemod.json";
    public static final String SOURCE_AUTO = "auto";
    public static final String SOURCE_GITHUB = "GitHub";
    public static final String SOURCE_CFPA = "CFPA";
    public static final String SOURCE_COMMUNITY = "Community Mirror";
    private static final List<String> KNOWN_SOURCES =
            Collections.unmodifiableList(Arrays.asList(SOURCE_GITHUB, SOURCE_CFPA, SOURCE_COMMUNITY));
    public static final int DEFAULT_INITIAL_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_CACHE_TTL_HOURS = 24;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean forceBottom = true;
    public String defaultSource = SOURCE_AUTO;
    public List<String> mirrorPriority = new ArrayList<>(Arrays.asList(SOURCE_CFPA, "Community", SOURCE_GITHUB));
    public boolean betaPack = false;
    public boolean mergeLoaders = false;
    public int initialTimeout = DEFAULT_INITIAL_TIMEOUT_SECONDS;
    public int cacheTtlHours = DEFAULT_CACHE_TTL_HOURS;
    public List<String> excludeMods = new ArrayList<>();
    public boolean onlyWhenChineseSystem = false;

    public static ModConfig defaults() {
        return new ModConfig();
    }

    public static ModConfig load(Path minecraftPath) {
        Path file = minecraftPath.resolve("config").resolve(FILE_NAME);
        JsonObject stored;
        try {
            stored = JsonParser.parseString(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (NoSuchFileException e) {
            stored = new JsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Log.warning("Failed to read %s, using default settings: %s", file, e);
            return defaults();
        }

        JsonObject defaultValues = GSON.toJsonTree(defaults()).getAsJsonObject();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : defaultValues.entrySet()) {
            JsonElement value = stored.get(entry.getKey());
            if (value == null || value.isJsonNull()) {
                stored.add(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        ModConfig config;
        try {
            config = GSON.fromJson(stored, ModConfig.class);
        } catch (JsonParseException e) {
            Log.warning("Invalid settings in %s, using defaults: %s", file, e);
            return defaults();
        }

        if (changed) {
            JsonObject updated = stored;
            try {
                FileUtil.writeAtomically(file, temporary ->
                        Files.write(temporary, GSON.toJson(updated).getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                Log.warning("Failed to write %s: %s", file, e);
            }
        }
        return config;
    }

    /**
     * @return the update check interval, {@code 0} checks on every launch
     */
    public long cacheTtlMillis() {
        return TimeUnit.HOURS.toMillis(Math.max(0, cacheTtlHours));
    }

    public Set<String> normalizedExcludeMods() {
        Set<String> result = new HashSet<>();
        if (excludeMods != null) {
            for (String mod : excludeMods) {
                if (mod != null && !mod.trim().isEmpty()) {
                    result.add(mod.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    /**
     * Uses the JVM default locale, which follows the operating system rather
     * than the in-game language.
     */
    public boolean isLanguageAllowed() {
        return !onlyWhenChineseSystem || isChineseLocale(Locale.getDefault());
    }

    static boolean isChineseLocale(Locale locale) {
        return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
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
