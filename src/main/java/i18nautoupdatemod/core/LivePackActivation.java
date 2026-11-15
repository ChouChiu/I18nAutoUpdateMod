package i18nautoupdatemod.core;

import i18nautoupdatemod.util.Log;
import i18nautoupdatemod.util.Version;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

public final class LivePackActivation {
    private static final Version FIRST_UNOBFUSCATED_VERSION = Version.from("26.1");

    private LivePackActivation() {
    }

    public static boolean supports(String minecraftVersion) {
        Version version = Version.from(minecraftVersion);
        return version != null && version.compareTo(FIRST_UNOBFUSCATED_VERSION) >= 0;
    }

    public static void schedule(String baseName, String packId, boolean forceBottom) {
        try {
            // Use the client API only on releases with stable, unobfuscated runtime names.
            Class<?> clientType = Class.forName("net.minecraft.client.Minecraft");
            Executor client = (Executor) clientType.getMethod("getInstance").invoke(null);
            if (client != null) {
                client.execute(() -> {
                    try {
                        activate(client, baseName, packId, forceBottom);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        logFailure(packId, e);
                    }
                });
            } else {
                Log.info("Client is not ready; %s will be enabled on next launch", packId);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailure(packId, e);
        }
    }

    static void activate(Object client, String baseName, String packId, boolean forceBottom)
            throws ReflectiveOperationException {
        Method getRepository = client.getClass().getMethod("getResourcePackRepository");
        Class<?> repositoryType = getRepository.getReturnType();
        Object repository = getRepository.invoke(client);
        repositoryType.getMethod("reload").invoke(repository);
        if (!(boolean) repositoryType.getMethod("isAvailable", String.class).invoke(repository, packId)) {
            Log.warning("Minecraft did not discover the generated pack %s", packId);
            return;
        }

        // getSelectedIds() is sorted by ID in some releases. Read the packs in load order.
        Collection<?> packs = (Collection<?>) repositoryType.getMethod("getSelectedPacks").invoke(repository);
        List<String> previous = new ArrayList<>(packs.size());
        for (Object pack : packs) {
            previous.add((String) pack.getClass().getMethod("getId").invoke(pack));
        }
        List<String> selected = ResourcePackSelection.withLanguagePack(previous, baseName, packId, forceBottom);
        if (!selected.equals(previous)) {
            Object options = client.getClass().getField("options").get(client);
            Method update = options.getClass().getMethod("updateResourcePacks", repositoryType);
            repositoryType.getMethod("setSelected", Collection.class).invoke(repository, selected);
            // Minecraft saves its current settings and reloads the resources itself.
            update.invoke(options, repository);
            Log.info("Enabled resource pack in Minecraft: %s", packId);
        }
    }

    private static void logFailure(String packId, Throwable error) {
        Log.warning("Could not enable %s during this session; will retry on next launch: %s", packId, error);
    }
}
