package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import i18nautoupdatemod.util.FileUtil;
import i18nautoupdatemod.util.Log;
import org.apache.commons.io.FileUtils;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameConfig {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    protected Map<String, String> configs = new LinkedHashMap<>();
    private final Path configFile;

    public GameConfig(Path configFile) throws Exception {
        this.configFile = configFile;
        if (!Files.exists(configFile)) {
            return;
        }
        this.configs = FileUtils.readLines(configFile.toFile(), StandardCharsets.UTF_8).stream()
                .map(it -> it.split(":", 2))
                .filter(it -> it.length == 2)
                .collect(Collectors.toMap(it -> it[0], it -> it[1], (a, b) -> a, LinkedHashMap::new));
    }

    public void writeToFile() throws Exception {
        FileUtil.ensureDirectory(configFile.getParent());
        Path temporary = configFile.resolveSibling(configFile.getFileName() + ".i18nautoupdatemod.part");
        try {
            FileUtils.writeLines(temporary.toFile(), "UTF-8", configs.entrySet().stream()
                    .map(it -> it.getKey() + ":" + it.getValue()).collect(Collectors.toList()));
            FileUtil.atomicReplace(temporary, configFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void addResourcePack(String baseName, String resourcePack) {
        addResourcePack(baseName, resourcePack, true);
    }

    public void addResourcePack(String baseName, String resourcePack, boolean forceBottom) {
        List<String> resourcePacks = GSON.fromJson(
                configs.computeIfAbsent("resourcePacks", it -> "[]"), STRING_LIST_TYPE);
        if (resourcePacks == null) {
            resourcePacks = new java.util.ArrayList<>();
        }
        int existingIndex = -1;
        for (int i = 0; i < resourcePacks.size(); i++) {
            if (resourcePacks.get(i).contains(baseName)) {
                existingIndex = i;
                break;
            }
        }
        // Remove older or misplaced language packs. options.txt lists packs from
        // bottom to top priority, so index 0 is the bottom of the list.
        resourcePacks = resourcePacks.stream()
                .filter(it -> !it.contains(baseName))
                .collect(Collectors.toList());
        resourcePacks.add(forceBottom || existingIndex < 0 ? 0 : existingIndex, resourcePack);
        configs.put("resourcePacks", GSON.toJson(resourcePacks));
        Log.info(String.format("Resource Packs: %s", configs.get("resourcePacks")));
//        configs.put("lang", "zh_cn");
    }
}
