package i18nautoupdatemod.core;

import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

final class ResourcePackSelection {
    private ResourcePackSelection() {
    }

    static List<String> withLanguagePack(List<String> previous, String baseName, String packId, boolean forceBottom) {
        List<String> selected = new ArrayList<>(previous.size() + 1);
        int existingIndex = -1;
        for (String id : previous) {
            if (id == null) {
                throw new JsonParseException("Null resource pack ID");
            }
            String name = id.startsWith("file/") ? id.substring("file/".length()) : id;
            if (name.equals(baseName + ".zip") || (name.startsWith(baseName + "-") && name.endsWith(".zip"))) {
                if (existingIndex < 0) {
                    existingIndex = selected.size();
                }
            } else {
                selected.add(id);
            }
        }
        // Minecraft's list runs from lowest to highest priority.
        selected.add(forceBottom || existingIndex < 0 ? 0 : existingIndex, packId);
        return selected;
    }
}
