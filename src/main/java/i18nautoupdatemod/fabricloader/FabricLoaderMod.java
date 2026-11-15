package i18nautoupdatemod.fabricloader;

import i18nautoupdatemod.I18nAutoUpdateMod;
import i18nautoupdatemod.util.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

public class FabricLoaderMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        Path gameDir = loader.getGameDir();
        Log.setMinecraftLogFile(gameDir);
        String minecraftVersion = loader.getModContainer("minecraft")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
        if (minecraftVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }
        Set<String> modIds = loader.getAllMods().stream()
                .map(mod -> mod.getMetadata().getId())
                .collect(Collectors.toSet());
        String loaderName = loader.isModLoaded("quilt_loader") ? "Quilt" : "Fabric";
        I18nAutoUpdateMod.init(gameDir, minecraftVersion, loaderName, modIds);
    }
}
