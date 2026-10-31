package i18nautoupdatemod.fabricloader;

import i18nautoupdatemod.I18nAutoUpdateMod;
import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.ModUtil;
import i18nautoupdatemod.util.Reflection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

//1.14-latest
public class FabricLoaderMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Log.setMinecraftLogFile(gameDir);
        String mcVersion = getMcVersion();
        if (mcVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }
        String loader = isQuilt() ? "Quilt" : "Fabric";
        // Mod ids and asset namespaces can differ, so scan the jars as well.
        HashSet<String> mods = getMods();
        mods.addAll(ModUtil.getModDomainsFromModsFolder(gameDir, mcVersion, loader));
        I18nAutoUpdateMod.init(gameDir, mcVersion, loader, mods);
    }

    private boolean isQuilt() {
        try {
            Class.forName("org.quiltmc.loader.api.QuiltLoader", false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private String getMcVersion() {
        try {
            // Fabric
            return (String) Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception ignored) {

        }
        try {
            // Quilt
            return (String) Reflection.clazz("org.quiltmc.loader.impl.QuiltLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception ignored) {

        }
        return null;
    }


    private HashSet<String> getMods() {
        HashSet<String> modList = new HashSet<>();
        try {
            // Fabric
            @SuppressWarnings("unchecked") final Map<String, Object> instance = (Map<String, Object>) Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("modMap").get();
            modList = new HashSet<>(instance.keySet());
            return modList;
        } catch (Exception ignored) {

        }
        try {
            // Quilt
            @SuppressWarnings("unchecked") final Map<String, Object> instance = (Map<String, Object>) Reflection.clazz("org.quiltmc.loader.impl.QuiltLoaderImpl")
                    .get("INSTANCE")
                    .get("modMap").get();
            modList = new HashSet<>(instance.keySet());
            return modList;
        } catch (Exception ignored) {

        }
        return modList;
    }
}
