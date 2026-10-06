package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.model.NormalData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CmaNwpUnitServiceTest {
    @Test
    void appliesConfiguredScaleAndOffsetByDataPath() {
        CmaDataProperties properties = new CmaDataProperties();
        CmaDataProperties.UnitConversion conversion = new CmaDataProperties.UnitConversion();
        conversion.setDataPath("tmp/*/*");
        conversion.setScale(1);
        conversion.setOffset(-273.15);
        conversion.setFrom("K");
        conversion.setTo("C");
        properties.setConversions(List.of(conversion));

        NormalData data = new NormalData();
        data.data = new float[][]{{273.15f, 300f}};
        new CmaNwpUnitService(properties).apply(data, "tmp", 103, 2);

        assertEquals(0f, data.data[0][0], 0.001f);
        assertEquals(26.85f, data.data[0][1], 0.001f);
        assertEquals("C", data.unit);
    }
}
