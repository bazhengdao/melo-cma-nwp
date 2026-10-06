package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.model.NormalData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CmaNwpDerivedServiceTest {
    private CmaNwpDerivedService derived;

    @BeforeEach
    void setUp() {
        CmaDataProperties properties = new CmaDataProperties();
        properties.setDerived(List.of(
                definition("rain24/1/0", "apcp/1/0", 24, 1, 24, null),
                definition("tmax24/103/2", "tmax/103/2", 3, 8, 24, "max"),
                definition("tmp_ch24/103/2", "tmp/103/2", 24, 1, 24, null),
                definition("tmax24_ch24/103/2", "tmax24/103/2", 24, 1, 48, null),
                definition("prmsl_ch24/101/0", "prmsl/101/0", 24, 1, 24, null)));
        CmaDataSourceRegistry sources = mock(CmaDataSourceRegistry.class);
        when(sources.contains(anyString())).thenReturn(false);
        derived = new CmaNwpDerivedService(mock(CmaNwpService.class), sources, properties);
    }

    @Test
    void calculatesRain24Tmax24AndTmax24Change() throws Exception {
        Map<String, NormalData> values = new HashMap<>();
        values.put("apcp/048", data(12));
        values.put("apcp/024", data(5));
        for (int hour = 3; hour <= 24; hour += 3) {
            values.put("tmax/" + String.format("%03d", hour), data(hour));
        }
        values.put("tmax24/048", data(40));
        values.put("tmax24/024", data(24));
        values.put("tmp/024", data(280));
        values.put("tmp/000", data(278));
        values.put("prmsl/024", data(1005));
        values.put("prmsl/000", data(1000));

        CmaNwpDerivedService.NormalDataLoader loader = (source, init, forecast, name, levelType, level) ->
                values.get(name + "/" + forecast);

        assertEquals(7, derived.normalData("cma-gfs", "2026092900", "048",
                "rain24", 1, 0, loader).data[0][0]);
        assertEquals(24, derived.normalData("cma-gfs", "2026092900", "024",
                "tmax24", 103, 2, loader).data[0][0]);
        assertEquals(2, derived.normalData("cma-gfs", "2026092900", "024",
                "tmp_ch24", 103, 2, loader).data[0][0]);
        assertEquals(16, derived.normalData("cma-gfs", "2026092900", "048",
                "tmax24_ch24", 103, 2, loader).data[0][0]);
        assertEquals(5, derived.normalData("cma-gfs", "2026092900", "024",
                "prmsl_ch24", 101, 0, loader).data[0][0]);
    }

    private static CmaDataProperties.Derived definition(String dataPath, String ref,
                                                         int step, int count, int minVT,
                                                         String aggr) {
        CmaDataProperties.Derived definition = new CmaDataProperties.Derived();
        definition.setDataPath(dataPath);
        definition.setRef(ref);
        definition.setStep(step);
        definition.setCount(count);
        definition.setMinVT(minVT);
        definition.setAggr(aggr);
        return definition;
    }

    private static NormalData data(float value) {
        NormalData data = new NormalData();
        data.data = new float[][]{{value}};
        return data;
    }
}
