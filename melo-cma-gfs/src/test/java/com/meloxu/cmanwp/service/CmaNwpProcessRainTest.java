package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.model.LatLng;
import com.meloxu.cmanwp.model.LatLngBounds;
import com.meloxu.cmanwp.model.NormalData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CmaNwpProcessRainTest {
    private CmaNwpDataService service;

    @BeforeEach
    void setUp() {
        CmaDataSourceRegistry sources = mock(CmaDataSourceRegistry.class);
        when(sources.contains("cma-gfs")).thenReturn(true);
        service = spy(new CmaNwpDataService(mock(CmaNwpService.class), new CmaNwpTileService(),
                sources, mock(CmaNwpDerivedService.class),
                mock(CmaNwpUnitService.class), new CmaNwpNormalDataCache(new CmaDataProperties())));
    }

    @Test
    void subtractsSelectedEndpointsWithoutChangingCachedApcp() throws Exception {
        NormalData end = data(42, Float.NaN);
        NormalData start = data(17, 2);
        stub("096", end);
        stub("039", start);
        stub("048", data(22, 3));
        NormalData result = service.minusData("cma-gfs/" + path("096"), "cma-gfs/" + path("039"));
        assertEquals(25, result.data[0][0]);
        assertTrue(Float.isNaN(result.data[0][1]));
        assertEquals("mm", result.unit);
        assertEquals(42, end.data[0][0]);
        assertEquals(17, start.data[0][0]);
        assertSame(result, service.minusData("cma-gfs/" + path("096"), "cma-gfs/" + path("039")));
        assertEquals(20, service.minusData("cma-gfs/" + path("096"), "cma-gfs/" + path("048")).data[0][0]);
        assertEquals("mm", service.minusMeta("cma-gfs/" + path("096"), "cma-gfs/" + path("039")).unit);
        byte[] tile = service.minusTile("cma-gfs/" + path("096"), "cma-gfs/" + path("039"), 0, 0, 0);
        // Binary tiles use big-endian int16 values with a scale of ten.
        assertEquals(250, java.nio.ByteBuffer.wrap(tile).getShort());
    }

    @Test
    void rejectsInvalidEndpointsBeforeLoadingData() {
        for (String start : new String[]{path("096"), path("099"), "apcp/1/0",
                "apcp/1/0/2026093000.039", "tmp/1/0/2026092900.039"}) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> service.minusData("cma-gfs/" + path("096"), "cma-gfs/" + start));
            assertEquals(400, error.getStatusCode().value());
        }
    }

    @Test
    void propagatesMissingStartAndRejectsMismatchedGrids() throws Exception {
        stub("096", data(42, 50));
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))
                .when(service).normalData("cma-gfs", "2026092900", "039", "apcp", 1, 0);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.minusData("cma-gfs/" + path("096"), "cma-gfs/" + path("039"))).getStatusCode().value());
        NormalData start = data(17, 20);
        start.res = 2;
        stub("039", start);
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> service.minusData("cma-gfs/" + path("096"), "cma-gfs/" + path("039"))).getStatusCode().value());
    }

    private void stub(String forecast, NormalData data) throws Exception {
        doReturn(data).when(service).normalData("cma-gfs", "2026092900", forecast, "apcp", 1, 0);
    }

    private static String path(String forecast) { return "apcp/1/0/2026092900." + forecast; }

    private static NormalData data(float first, float second) {
        NormalData data = new NormalData();
        data.unit = "mm";
        data.data = new float[][]{{first, second}};
        data.res = 1;
        data.bounds = new LatLngBounds(new LatLng(-90, -180), new LatLng(-90, -179));
        return data;
    }
}
