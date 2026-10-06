package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.model.NormalData;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CmaNwpNormalDataCache {
    private final int maxEntries;
    private final Map<Key, NormalData> entries;

    public CmaNwpNormalDataCache(CmaDataProperties properties) {
        maxEntries = properties.getNormalDataCacheSize();
        if (maxEntries < 1) {
            throw new IllegalStateException("normalDataCacheSize must be at least 1");
        }
        entries = new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, NormalData> eldest) {
                return size() > CmaNwpNormalDataCache.this.maxEntries;
            }
        };
    }

    public synchronized NormalData get(Key key) {
        return entries.get(key);
    }

    public synchronized void put(Key key, NormalData value) {
        entries.put(key, value);
    }

    synchronized int size() {
        return entries.size();
    }

    public record Key(String sourceId, String inittime, String forecast,
                      String shortName, int levelType, double level) {
    }
}
