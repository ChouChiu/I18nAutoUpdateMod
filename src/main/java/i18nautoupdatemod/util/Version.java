package i18nautoupdatemod.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Version implements Comparable<Version> {
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^[0-9]+(?:\\.[0-9]+)*");

    private final List<Integer> components;

    private Version(List<Integer> components) {
        this.components = components;
    }

    public static Version from(String version) {
        if (version == null) {
            return null;
        }
        Matcher prefix = NUMBER_PREFIX.matcher(version);
        if (!prefix.find()) {
            return null;
        }
        List<Integer> components = new ArrayList<>();
        try {
            for (String component : prefix.group().split("\\.")) {
                components.add(Integer.parseInt(component));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new Version(components);
    }

    @Override
    public int compareTo(Version other) {
        int count = Math.min(components.size(), other.components.size());
        for (int i = 0; i < count; i++) {
            int comparison = Integer.compare(components.get(i), other.components.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(components.size(), other.components.size());
    }
}
