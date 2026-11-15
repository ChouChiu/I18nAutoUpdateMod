package i18nautoupdatemod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import i18nautoupdatemod.util.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

final class PackArchive implements AutoCloseable {
    static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final long MAX_METADATA_BYTES = 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    static final String METADATA = "pack.mcmeta";
    private static final OutputStream DISCARD = new OutputStream() {
        @Override
        public void write(int value) {
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
        }
    };

    private final ZipFile zip;
    private final JsonObject metadata;

    private PackArchive(ZipFile zip, JsonObject metadata) {
        this.zip = zip;
        this.metadata = metadata;
    }

    static PackArchive open(Path path) throws IOException {
        ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8);
        try {
            if (zip.size() > MAX_ENTRIES) {
                throw new ZipException("Too many entries in " + path);
            }
            Set<String> names = new HashSet<>();
            long remaining = MAX_EXPANDED_BYTES;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!isSafeName(name) || !names.add(name)) {
                    throw new ZipException("Unsafe or duplicate ZIP entry: " + name);
                }
                long size = entry.getSize();
                if (size < 0 || size > remaining) {
                    throw new ZipException("Expanded resource pack exceeds limit: " + path);
                }
                remaining -= size;
            }
            ZipEntry entry = zip.getEntry(METADATA);
            if (entry == null || entry.isDirectory()) {
                throw new ZipException("Missing " + METADATA + " in " + path);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copyEntry(zip, entry, bytes, MAX_METADATA_BYTES);
            return new PackArchive(zip, parseMetadata(bytes.toByteArray()));
        } catch (IOException | RuntimeException | Error e) {
            try {
                zip.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    static void validateDownload(Path path) throws IOException {
        try (PackArchive archive = open(path)) {
            for (Enumeration<? extends ZipEntry> entries = archive.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && !METADATA.equals(entry.getName())) {
                    archive.copyEntry(entry, DISCARD, entry.getSize());
                }
            }
        }
    }

    static JsonObject readMetadata(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamUtil.copyLimited(input, output, MAX_METADATA_BYTES);
        return parseMetadata(output.toByteArray());
    }

    private static JsonObject parseMetadata(byte[] bytes) throws IOException {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Invalid " + METADATA, e);
        }
    }

    private static boolean isSafeName(String name) {
        return !name.isEmpty() && !name.startsWith("/") && name.indexOf('\\') < 0
                && name.indexOf(':') < 0 && name.indexOf(0) < 0 && !name.contains("//")
                && !("/" + name + "/").contains("/../") && !("/" + name + "/").contains("/./");
    }

    JsonObject metadata() {
        return metadata;
    }

    Enumeration<? extends ZipEntry> entries() {
        return zip.entries();
    }

    long copyEntry(ZipEntry entry, OutputStream output, long limit) throws IOException {
        return copyEntry(zip, entry, output, limit);
    }

    private static long copyEntry(ZipFile zip, ZipEntry entry, OutputStream output, long limit)
            throws IOException {
        try (CheckedInputStream input = new CheckedInputStream(zip.getInputStream(entry), new CRC32())) {
            long bytes = StreamUtil.copyLimited(input, output, Math.min(entry.getSize(), limit));
            if (bytes != entry.getSize() || input.getChecksum().getValue() != entry.getCrc()) {
                throw new ZipException("Corrupt resource pack entry: " + entry.getName());
            }
            return bytes;
        }
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
