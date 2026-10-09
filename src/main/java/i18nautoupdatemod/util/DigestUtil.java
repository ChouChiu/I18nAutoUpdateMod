package i18nautoupdatemod.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestUtil {
    public static String md5Hex(Path file) throws IOException, NoSuchAlgorithmException {
        return digestHex(file, "MD5");
    }

    public static String sha256Hex(Path file) throws IOException, NoSuchAlgorithmException {
        return digestHex(file, "SHA-256");
    }

    private static String digestHex(Path file, String algorithm)
            throws IOException, NoSuchAlgorithmException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest dig = MessageDigest.getInstance(algorithm);

            final byte[] buf = new byte[114514];

            for (int read = is.read(buf); read != -1; read = is.read(buf)) {
                dig.update(buf, 0, read);
            }

            return hexString(dig.digest());
        }
    }

    private static final String HEX = "0123456789ABCDEF";

    public static String hexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte d : data) {
            sb.append(HEX.charAt((d & 0xf0) >>> 4));
            sb.append(HEX.charAt(d & 0xf));
        }
        return sb.toString();
    }
}
