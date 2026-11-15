package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GameConfig {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private static final String PACKS_KEY = "resourcePacks:";
    private final List<String> lines;
    private final Path configFile;

    public GameConfig(Path configFile) throws IOException {
        this.configFile = configFile;
        List<String> stored;
        try {
            stored = Files.readAllLines(configFile, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            stored = Collections.emptyList();
        }
        lines = new ArrayList<>(stored);
    }

    public void writeToFile() throws IOException {
        FileUtil.writeAtomically(configFile, temporary -> Files.write(temporary, lines, StandardCharsets.UTF_8));
    }

    public boolean addResourcePack(String baseName, String resourcePack) {
        return addResourcePack(baseName, resourcePack, true);
    }

    public boolean addResourcePack(String baseName, String resourcePack, boolean forceBottom) {
        int line = lastPackLine();
        List<String> previous = line < 0
                ? null
                : GSON.fromJson(lines.get(line).substring(PACKS_KEY.length()), STRING_LIST_TYPE);
        List<String> packs = ResourcePackSelection.withLanguagePack(
                previous == null ? Collections.emptyList() : previous, baseName, resourcePack, forceBottom);
        if (packs.equals(previous)) {
            return false;
        }
        String updated = PACKS_KEY + GSON.toJson(packs);
        if (line < 0) {
            lines.add(updated);
        } else {
            lines.set(line, updated);
        }
        Log.info("Resource packs: %s", packs);
        return true;
    }

    private int lastPackLine() {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).startsWith(PACKS_KEY)) {
                return i;
            }
        }
        return -1;
    }
}
