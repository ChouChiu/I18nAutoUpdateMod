package net.minecraft.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/** Test double for the public, unobfuscated client methods used by live activation. */
public final class Minecraft implements Executor {
    public static Minecraft instance;
    public final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    public final Options options = new Options();
    private final PackRepository repository = new PackRepository();

    public static Minecraft getInstance() {
        return instance;
    }

    public PackRepository getResourcePackRepository() {
        return repository;
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
    }

    public static final class PackRepository {
        private List<String> selected = Arrays.asList("vanilla", "file/z-user.zip", "file/a-user.zip");
        public String discoveredPack;

        public void reload() {
        }

        public boolean isAvailable(String id) {
            return id.equals(discoveredPack);
        }

        public Collection<Pack> getSelectedPacks() {
            return selected.stream().map(Pack::new).collect(Collectors.toList());
        }

        public void setSelected(Collection<String> ids) {
            selected = new ArrayList<>(ids);
        }
    }

    public static final class Options {
        public List<String> savedPacks;

        public void updateResourcePacks(PackRepository repository) {
            savedPacks = new ArrayList<>(repository.selected);
        }
    }

    public static final class Pack {
        private final String id;

        Pack(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
