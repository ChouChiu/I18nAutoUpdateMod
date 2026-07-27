package i18nautoupdatemod.launchwrapper;

import i18nautoupdatemod.I18nAutoUpdateMod;
import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.ModUtil;
import i18nautoupdatemod.util.Reflection;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;

//1.6-1.12.2
public class LaunchWrapperTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        Log.setMinecraftLogFile(gameDir.toPath());
        String mcVersion = getMcVersion();
        if (mcVersion == null) {
            Log.warning("Failed to get minecraft version.");
            return;
        }
        I18nAutoUpdateMod.init(gameDir.toPath(), mcVersion, "Forge", ModUtil.getModDomainsFromModsFolder(gameDir.toPath(), mcVersion, "Forge"));
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {

    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    private String getMcVersion() {
        try {
            // 1.6~1.7.10
            // 1.6: https://github.com/MinecraftForge/FML/blob/16launch/common/cpw/mods/fml/relauncher/FMLInjectionData.java#L32
            // 1.7.10: https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/fml/src/main/java/cpw/mods/fml/relauncher/FMLInjectionData.java#L32
            return (String)
                    Reflection.clazz("cpw.mods.fml.relauncher.FMLInjectionData").get("mccversion").get();
        } catch (Exception ignored) {
        }

        try {
            // 1.8
            // https://github.com/MinecraftForge/FML/blob/1.8/src/main/java/net/minecraftforge/fml/relauncher/FMLInjectionData.java#L32
            return (String)
                    Reflection.clazz("net.minecraftforge.fml.relauncher.FMLInjectionData").get("mccversion").get();
        } catch (Exception ignored) {
        }

        try {
            // 1.8.8~1.12.2
            // 1.8.8: https://github.com/MinecraftForge/MinecraftForge/blob/1.8.8/src/main/java/net/minecraftforge/common/ForgeVersion.java#L42
            // 1.12.2: https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/src/main/java/net/minecraftforge/common/ForgeVersion.java#L64
            return (String) Reflection.clazz("net.minecraftforge.common.ForgeVersion").get("mcVersion").get();
        } catch (Exception ignored) {
        }
        return null;
    }
}
