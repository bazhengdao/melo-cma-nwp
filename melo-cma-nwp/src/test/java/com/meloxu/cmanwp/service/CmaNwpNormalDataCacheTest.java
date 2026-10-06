package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.model.NormalData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CmaNwpNormalDataCacheTest {
    @Test
    void evictsLeastRecentlyUsedNormalData() {
        CmaDataProperties properties = new CmaDataProperties();
        properties.setNormalDataCacheSize(2);
        CmaNwpNormalDataCache cache = new CmaNwpNormalDataCache(properties);
        var firstKey = key("tmp", "000");
        var secondKey = key("tmp", "003");
        var thirdKey = key("tmp", "006");
        var first = data(1);
        var second = data(2);
        var third = data(3);

        cache.put(firstKey, first);
        cache.put(secondKey, second);
        assertSame(first, cache.get(firstKey));
        cache.put(thirdKey, third);

        assertSame(first, cache.get(firstKey));
        assertEquals(null, cache.get(secondKey));
        assertSame(third, cache.get(thirdKey));
        assertEquals(2, cache.size());
    }

    private static CmaNwpNormalDataCache.Key key(String shortName, String forecast) {
        return new CmaNwpNormalDataCache.Key("cma-gfs", "2026092900", forecast,
                shortName, 103, 2);
    }

    private static NormalData data(float value) {
        NormalData data = new NormalData();
        data.data = new float[][]{{value}};
        return data;
    }
}
