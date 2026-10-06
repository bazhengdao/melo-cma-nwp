package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.model.LatLng;
import com.meloxu.cmanwp.model.LatLngBounds;
import com.meloxu.cmanwp.model.NormalData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmaNwpContourServiceTest {
    private CmaNwpDataService dataService;
    private CmaDataProperties properties;
    private CmaDataSourceRegistry sources;
    private CmaNwpContourService contourService;

    @BeforeEach
    void setUp() throws Exception {
        dataService = mock(CmaNwpDataService.class);
        properties = new CmaDataProperties();
        sources = mock(CmaDataSourceRegistry.class);
        when(sources.contains(anyString())).thenReturn(false);
        when(dataService.normalDataForPath(anyString())).thenReturn(sampleData());
        contourService = new CmaNwpContourService(dataService, properties, sources,
                new CmaNwpContourCache(properties));
    }

    @Test
    void usesConfiguredLevelsWhenCallerDoesNotProvideClevs() throws Exception {
        CmaDataProperties.Contour contour = new CmaDataProperties.Contour();
        contour.setDataPath("tmp/103/2");
        contour.setClevs(List.of(1.0));
        properties.setContours(List.of(contour));

        Map<String, Object> result = contourService.contour("tmp/103/2/2026092900.024", null);

        assertEquals("FeatureCollection", result.get("type"));
        List<?> features = (List<?>) result.get("features");
        assertFalse(features.isEmpty());
        Map<?, ?> firstFeature = (Map<?, ?>) features.get(0);
        assertEquals(1.0, ((Map<?, ?>) firstFeature.get("properties")).get("level"));
    }

    @Test
    void callerLevelsOverrideConfiguration() throws Exception {
        CmaDataProperties.Contour contour = new CmaDataProperties.Contour();
        contour.setDataPath("tmp/103/2");
        contour.setClevs(List.of(1.0));
        properties.setContours(List.of(contour));

        Map<String, Object> result = contourService.contour("tmp/103/2/2026092900.024", "0.5");

        List<?> features = (List<?>) result.get("features");
        assertFalse(features.isEmpty());
        Map<?, ?> firstFeature = (Map<?, ?>) features.get(0);
        assertEquals(0.5, ((Map<?, ?>) firstFeature.get("properties")).get("level"));
    }

    @Test
    void roundsCoordinatesToFourDecimalPlaces() throws Exception {
        Map<String, Object> result = contourService.contour("tmp/103/2/2026092900.024", "1");

        List<?> features = (List<?>) result.get("features");
        for (Object feature : features) {
            List<?> coordinates = (List<?>) ((Map<?, ?>) ((Map<?, ?>) feature).get("geometry"))
                    .get("coordinates");
            for (Object coordinate : coordinates) {
                for (Object value : (List<?>) coordinate) {
                    double number = (double) value;
                    assertEquals(Math.round(number * 10_000d) / 10_000d, number);
                }
            }
        }
    }

    @Test
    void requiresLevelsWhenNoConfigurationExists() {
        assertThrows(ResponseStatusException.class,
                () -> contourService.contour("tmp/103/2/2026092900.024", null));
    }

    @Test
    void reusesCachedContourForSamePathAndLevels() throws Exception {
        contourService.contour("tmp/103/2/2026092900.024", "1");
        contourService.contour("tmp/103/2/2026092900.024", "1.0");

        verify(dataService, times(1)).normalDataForPath("tmp/103/2/2026092900.024");
    }

    private static NormalData sampleData() {
        NormalData data = new NormalData();
        data.res = 1;
        data.bounds = new LatLngBounds(new LatLng(0, 0), new LatLng(2, 2));
        data.data = new float[][]{
                {0, 0, 0},
                {0, 3, 0},
                {0, 0, 0}
        };
        return data;
    }
}
