package i18nautoupdatemod.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DigestUtil {
    private DigestUtil() {
    }

    public static String md5Hex(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest dig = newMd5();

            final byte[] buf = new byte[8192];

            for (int read = is.read(buf); read != -1; read = is.read(buf)) {
                dig.update(buf, 0, read);
            }

            return hexString(dig.digest());
        }
    }

    private static MessageDigest newMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required by the Java platform", e);
        }
    }

    private static final String HEX = "0123456789ABCDEF";

    private static String hexString(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte d : data) {
            sb.append(HEX.charAt((d & 0xf0) >>> 4));
            sb.append(HEX.charAt(d & 0xf));
        }
        return sb.toString();
    }
}
