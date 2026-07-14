package model.ai;

import java.util.HashMap;
import java.util.Map;

public class AiCacheUtil {
    private static final Map<String, String> cache = new HashMap<>();

    public static void put(String key, String value) {
        cache.put(key, value);
    }

    public static String get(String key) {
        return cache.get(key);
    }

    public static boolean contains(String key) {
        return cache.containsKey(key);
    }
}