package com.bmi.model.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 缓存工具
 * 对应 docs/ai_design.md §5 缓存策略
 * 相同身体数据缓存 10 分钟（600000ms）
 */
public class AiCacheUtil {
    private static final long CACHE_EXPIRE_MS = 10 * 60 * 1000; // 10分钟
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public static void put(String key, String value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + CACHE_EXPIRE_MS));
    }

    public static String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        // 检查是否过期
        if (System.currentTimeMillis() > entry.expireTime) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    public static boolean contains(String key) {
        return get(key) != null;
    }

    public static void clear() {
        cache.clear();
    }

    private static class CacheEntry {
        String value;
        long expireTime;
        CacheEntry(String value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }
    }
}