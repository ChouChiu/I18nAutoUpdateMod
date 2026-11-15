package i18nautoupdatemod.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class ModUtil {
    private static final int MAX_NESTED_DEPTH = 4;
    private static final long MAX_NESTED_EXPANDED_BYTES = 128L * 1024 * 1024;

    private ModUtil() {
    }

    public static Set<String> getModDomainsFromModsFolder(Path minecraftPath) {
        Set<String> domains = new HashSet<>();
        Path mods = minecraftPath.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return domains;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : jars) {
                if (Files.isRegularFile(jar)) {
                    scanJar(jar, domains);
                }
            }
        } catch (IOException | DirectoryIteratorException e) {
            Log.warning("Failed to list mods in %s: %s", mods, e);
        }
        return domains;
    }

    private static void scanJar(Path jar, Set<String> domains) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ScanBudget budget = new ScanBudget();
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                addDomain(name, domains);
                if (isNestedJar(entry)) {
                    try (InputStream input = zip.getInputStream(entry)) {
                        scanNested(input, domains, 1, budget);
                    } catch (IOException e) {
                        Log.warning("Failed to scan %s in %s: %s", name, jar, e);
                    }
                }
            }
        } catch (IOException e) {
            Log.warning("Failed to read mod JAR %s: %s", jar, e);
        }
    }

    private static void scanNested(InputStream input, Set<String> domains, int depth, ScanBudget budget)
            throws IOException {
        // Closing a nested reader must not close its enclosing ZIP reader.
        try (ZipInputStream zip = new ZipInputStream(new FilterInputStream(input) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value != -1) {
                    budget.consume(1);
                }
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                int read = super.read(bytes, offset, length);
                if (read > 0) {
                    budget.consume(read);
                }
                return read;
            }

            @Override
            public void close() {
            }
        })) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                addDomain(entry.getName(), domains);
                if (depth < MAX_NESTED_DEPTH && isNestedJar(entry)) {
                    scanNested(zip, domains, depth + 1, budget);
                }
                for (int read; (read = zip.read(budget.buffer)) != -1; ) {
                    budget.consume(read);
                }
            }
        }
    }

    private static boolean isNestedJar(ZipEntry entry) {
        return !entry.isDirectory() && entry.getName().endsWith(".jar");
    }

    private static void addDomain(String name, Set<String> domains) {
        if (name.startsWith("assets/")) {
            int end = name.indexOf('/', "assets/".length());
            if (end > "assets/".length()) {
                domains.add(name.substring("assets/".length(), end));
            }
        }
    }

    private static final class ScanBudget {
        final byte[] buffer = new byte[8192];
        long remaining = MAX_NESTED_EXPANDED_BYTES;

        void consume(int bytes) throws IOException {
            remaining -= bytes;
            if (remaining < 0) {
                throw new IOException("Nested JAR contents exceed scan limit");
            }
        }
    }
}
