package i18nautoupdatemod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class LocationDetectUtil {
    private static final AtomicReference<Boolean> CACHED = new AtomicReference<>();
    private static final String[] GEO_APIS = {
            "https://mips.kugou.com/check/iscn?&format=json",
            "https://api.ip.sb/geoip"
    };

    private LocationDetectUtil() {
    }

    public static boolean isMainlandChina() {
        Boolean cached = CACHED.get();
        if (cached != null) {
            return cached;
        }

        boolean detected = detectMainlandChina(GEO_APIS);
        CACHED.compareAndSet(null, detected);
        return CACHED.get();
    }

    static boolean detectMainlandChina(String[] geoApis) {
        for (String api : geoApis) {
            try {
                String response = AssetUtil.getString(
                        api,
                        (int) TimeUnit.SECONDS.toMillis(5),
                        (int) TimeUnit.SECONDS.toMillis(5));
                Boolean detected = parseResponse(response);
                if (detected != null) {
                    Log.info("Location detected: %s mainland China",
                            detected ? "inside" : "outside");
                    return detected;
                }
            } catch (Exception e) {
                Log.debug("Location detection failed for %s: %s", api, e);
            }
        }

        Log.info("Location detection unavailable: defaulting to GitHub-first ordering");
        return false;
    }

    static Boolean parseResponse(String response) {
        JsonObject object = JsonParser.parseString(response).getAsJsonObject();
        JsonElement flag = object.get("flag");
        if (flag != null && !flag.isJsonNull()) {
            if (flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean()) {
                return flag.getAsBoolean();
            }
            return flag.getAsInt() == 1;
        }

        JsonElement countryCode = object.get("country_code");
        if (countryCode != null && !countryCode.isJsonNull()) {
            return "CN".equalsIgnoreCase(countryCode.getAsString());
        }
        return null;
    }

    static void resetForTests() {
        CACHED.set(null);
    }
}
