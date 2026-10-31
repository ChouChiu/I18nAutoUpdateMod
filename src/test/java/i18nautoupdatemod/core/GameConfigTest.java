package i18nautoupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConfigTest {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();

    @TempDir
    Path temporaryDirectory;

    @Test
    void placesLanguagePackAtBottomAndRepositionsExistingVersion() throws Exception {
        Path options = temporaryDirectory.resolve("options.txt");
        Files.write(
                options,
                ("resourcePacks:[\"vanilla\",\"file/user.zip\","
                        + "\"file/Minecraft-Mod-Language-Modpack-old.zip\"]\n")
                        .getBytes(StandardCharsets.UTF_8));

        GameConfig config = new GameConfig(options);
        config.addResourcePack(
                "Minecraft-Mod-Language-Modpack",
                "file/Minecraft-Mod-Language-Modpack-Converted-26.1.2.zip");
        config.writeToFile();

        String resourcePacksLine = Files.readAllLines(options, StandardCharsets.UTF_8)
                .stream()
                .filter(line -> line.startsWith("resourcePacks:"))
                .findFirst()
                .orElseThrow(AssertionError::new);
        List<String> resourcePacks = GSON.fromJson(
                resourcePacksLine.substring("resourcePacks:".length()),
                STRING_LIST_TYPE);

        assertEquals(
                "file/Minecraft-Mod-Language-Modpack-Converted-26.1.2.zip",
                resourcePacks.get(0));
        assertEquals("vanilla", resourcePacks.get(1));
        assertEquals("file/user.zip", resourcePacks.get(2));
        assertEquals(3, resourcePacks.size());
    }

    @Test
    void keepsUserPositionWhenForceBottomIsDisabled() throws Exception {
        Path options = temporaryDirectory.resolve("options.txt");
        Files.write(
                options,
                ("resourcePacks:[\"vanilla\",\"file/Minecraft-Mod-Language-Modpack-old.zip\","
                        + "\"file/user.zip\",\"file/Minecraft-Mod-Language-Modpack-dup.zip\"]\n")
                        .getBytes(StandardCharsets.UTF_8));

        GameConfig config = new GameConfig(options);
        config.addResourcePack("Minecraft-Mod-Language-Modpack", "file/Minecraft-Mod-Language-Modpack-new.zip", false);

        List<String> resourcePacks = GSON.fromJson(config.configs.get("resourcePacks"), STRING_LIST_TYPE);
        assertEquals(3, resourcePacks.size());
        assertEquals("vanilla", resourcePacks.get(0));
        assertEquals("file/Minecraft-Mod-Language-Modpack-new.zip", resourcePacks.get(1));
        assertEquals("file/user.zip", resourcePacks.get(2));
    }

    @Test
    void addsNewPackAtBottomWhenForceBottomIsDisabled() throws Exception {
        Path options = temporaryDirectory.resolve("options.txt");
        Files.write(options, "resourcePacks:[\"vanilla\"]\n".getBytes(StandardCharsets.UTF_8));

        GameConfig config = new GameConfig(options);
        config.addResourcePack("Minecraft-Mod-Language-Modpack", "file/Minecraft-Mod-Language-Modpack-1.zip", false);

        List<String> resourcePacks = GSON.fromJson(config.configs.get("resourcePacks"), STRING_LIST_TYPE);
        assertEquals("file/Minecraft-Mod-Language-Modpack-1.zip", resourcePacks.get(0));
        assertEquals("vanilla", resourcePacks.get(1));
    }

    @Test
    void repeatedResourcePacksLineKeepsLastValue() throws Exception {
        Path options = temporaryDirectory.resolve("options.txt");
        Files.write(
                options,
                ("resourcePacks:[\"file/first.zip\"]\n"
                        + "resourcePacks:[\"file/second.zip\"]\n")
                        .getBytes(StandardCharsets.UTF_8));

        GameConfig config = new GameConfig(options);
        config.addResourcePack("Minecraft-Mod-Language-Modpack", "file/Minecraft-Mod-Language-Modpack-a.zip");

        List<String> resourcePacks = GSON.fromJson(config.configs.get("resourcePacks"), STRING_LIST_TYPE);
        assertEquals("file/second.zip", resourcePacks.get(1));
        assertEquals(2, resourcePacks.size());
    }

    @Test
    void reportsWhetherResourcePackListChanged() throws Exception {
        Path options = temporaryDirectory.resolve("options.txt");
        Files.write(
                options,
                "resourcePacks:[\"file/Minecraft-Mod-Language-Modpack-a.zip\",\"vanilla\"]\n"
                        .getBytes(StandardCharsets.UTF_8));

        GameConfig config = new GameConfig(options);
        assertFalse(config.addResourcePack(
                "Minecraft-Mod-Language-Modpack", "file/Minecraft-Mod-Language-Modpack-a.zip"));
        assertTrue(config.addResourcePack(
                "Minecraft-Mod-Language-Modpack", "file/Minecraft-Mod-Language-Modpack-b.zip"));
    }
}
