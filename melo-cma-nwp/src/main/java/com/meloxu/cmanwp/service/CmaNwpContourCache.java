package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CmaNwpContourCache {
    private final int maxEntries;
    private final Map<Key, Map<String, Object>> entries;

    public CmaNwpContourCache(CmaDataProperties properties) {
        maxEntries = properties.getContourCacheSize();
        if (maxEntries < 1) {
            throw new IllegalStateException("contourCacheSize must be at least 1");
        }
        entries = new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Map<String, Object>> eldest) {
                return size() > CmaNwpContourCache.this.maxEntries;
            }
        };
    }

    public synchronized Map<String, Object> get(Key key) {
        return entries.get(key);
    }

    public synchronized void put(Key key, Map<String, Object> value) {
        entries.put(key, value);
    }

    synchronized int size() {
        return entries.size();
    }

    public record Key(String dataPath, List<Double> levels) {
    }
}
