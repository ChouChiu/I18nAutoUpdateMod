package i18nautoupdatemod.neoforge;

import i18nautoupdatemod.I18nAutoUpdateMod;
import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.ModUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

import java.nio.file.Path;

@Mod(value = I18nAutoUpdateMod.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeMod {
    public NeoForgeMod() {
        FMLLoader loader = FMLLoader.getCurrent();
        if (loader == null) {
            Log.warning("NeoForge loader not found");
            return;
        }

        Path minecraftPath = loader.getGameDir();
        if (minecraftPath == null) {
            Log.warning("Minecraft path not found");
            return;
        }
        Log.setMinecraftLogFile(minecraftPath);

        String minecraftVersion = getMinecraftVersion(loader);
        if (minecraftVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }

        I18nAutoUpdateMod.init(
                minecraftPath,
                minecraftVersion,
                "NeoForge",
                ModUtil.getModDomainsFromModsFolder(
                        minecraftPath, minecraftVersion, "NeoForge"));
    }

    private String getMinecraftVersion(FMLLoader loader) {
        String version = loader.getVersionInfo() == null
                ? null
                : loader.getVersionInfo().mcVersion();
        if (version != null && !version.isEmpty()) {
            return version;
        }
        return getMinecraftVersion(loader.getProgramArgs().getArguments());
    }

    private String getMinecraftVersion(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if ("--fml.mcversion".equalsIgnoreCase(args[index])) {
                return args[index + 1];
            }
        }
        return null;
    }
}
