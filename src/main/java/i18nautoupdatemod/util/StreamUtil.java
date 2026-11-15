package i18nautoupdatemod.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamUtil {
    private StreamUtil() {
    }

    public static long copyLimited(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long bytes = 0;
        for (int read; (read = input.read(buffer)) != -1; ) {
            if (read > limit - bytes) {
                throw new IOException("Stream exceeds " + limit + " bytes");
            }
            output.write(buffer, 0, read);
            bytes += read;
        }
        return bytes;
    }
}
