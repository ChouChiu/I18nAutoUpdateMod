package i18nautoupdatemod.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModUtilTest {
    @TempDir
    Path gameDir;

    @Test
    void readsNestedNamespacesAndContinuesScanningAfterNestedJar() throws IOException {
        byte[] inner = zip("assets/inner/lang/zh_cn.json", new byte[] {1});
        ByteArrayOutputStream middle = new ByteArrayOutputStream();
        try (ZipOutputStream jar = new ZipOutputStream(middle)) {
            addEntry(jar, "jars/inner.jar", inner);
            addEntry(jar, "assets/middle/models/example.json", new byte[] {1});
        }
        Path mods = Files.createDirectory(gameDir.resolve("mods"));
        try (ZipOutputStream jar = new ZipOutputStream(Files.newOutputStream(mods.resolve("outer.jar")))) {
            addEntry(jar, "META-INF/jarjar/middle.jar", middle.toByteArray());
            addEntry(jar, "assets/outer/lang/zh_cn.json", new byte[] {1});
        }
        assertEquals(new HashSet<>(Arrays.asList("outer", "middle", "inner")),
                ModUtil.getModDomainsFromModsFolder(gameDir));
    }

    private static byte[] zip(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream jar = new ZipOutputStream(bytes)) {
            addEntry(jar, name, content);
        }
        return bytes.toByteArray();
    }

    private static void addEntry(ZipOutputStream jar, String name, byte[] content) throws IOException {
        jar.putNextEntry(new ZipEntry(name));
        jar.write(content);
        jar.closeEntry();
    }
}
