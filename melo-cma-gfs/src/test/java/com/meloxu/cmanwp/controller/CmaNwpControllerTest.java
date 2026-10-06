package com.meloxu.cmanwp.controller;

import com.meloxu.cmanwp.service.CmaNwpContourService;
import com.meloxu.cmanwp.service.CmaNwpDataService;
import com.meloxu.cmanwp.service.CmaNwpService;
import com.meloxu.cmanwp.model.NormalInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CmaNwpController.class)
class CmaNwpControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CmaNwpService service;

    @MockBean
    private CmaNwpDataService dataService;

    @MockBean
    private CmaNwpContourService contourService;

    @Test
    void initTimesEndpointUsesDefaultDataCode() throws Exception {
        when(service.initTimes("cma-gfs")).thenReturn(List.of(Map.of("utc", "2026092900")));

        mockMvc.perform(get("/api/cma-nwp/inittimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].utc").value("2026092900"));
    }

    @Test
    void validTimesEndpointUsesDataCodeFromDataPath() throws Exception {
        when(service.validTimes("cma-gfs", "2026092900", "hgt/100/500"))
                .thenReturn(List.of(Map.of("validTime", "024", "fcstTime", "2026093000")));

        mockMvc.perform(get("/api/cma-nwp/validtimes")
                        .param("inittime", "2026092900")
                        .param("dataPath", "cma-gfs/hgt/100/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validTime").value("024"));
    }

    @Test
    void validTimesEndpointPassesDerivedDataPath() throws Exception {
        when(service.validTimes("cma-gfs", "2026092900", "rain24/1/0"))
                .thenReturn(List.of(Map.of("validTime", "024")));

        mockMvc.perform(get("/api/cma-nwp/validtimes")
                        .param("inittime", "2026092900")
                        .param("dataPath", "cma-gfs/rain24/1/0/2026092900.024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validTime").value("024"));
    }

    @Test
    void catalogEndpointPassesRunAndForecast() throws Exception {
        when(service.messageCatalog("cma-gfs", "2026092900", "024"))
                .thenReturn(Map.of("count", 1));

        mockMvc.perform(get("/api/cma-nwp/messages/catalog")
                        .param("inittime", "2026092900")
                        .param("forecast", "024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void metaEndpointReturnsMetadata() throws Exception {
        when(dataService.meta("cma-gfs/tmp/103/2/2026092900.024")).thenReturn(new NormalInfo());

        mockMvc.perform(get("/api/cma-nwp/meta")
                        .param("dataPath", "cma-gfs/tmp/103/2/2026092900.024"))
                .andExpect(status().isOk());
    }

    @Test
    void tileQueryEndpointReturnsBinaryBody() throws Exception {
        when(dataService.tile("cma-gfs/tmp/103/2/2026092900.024", 3, 2, 1))
                .thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/cma-nwp/tile")
                        .param("dataPath", "cma-gfs/tmp/103/2/2026092900.024")
                        .param("z", "3")
                        .param("x", "2")
                        .param("y", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/octet-stream"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void tilePathEndpointBuildsUnifiedDataPath() throws Exception {
        when(dataService.tile("CMA-GFS/tmp/103/2/2026092900.024", 3, 2, 1))
                .thenReturn(new byte[]{4});

        mockMvc.perform(get("/api/cma-nwp/tile/CMA-GFS/tmp/103/2/2026092900.024/3/2/1.bin"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{4}));
        verify(dataService).tile("CMA-GFS/tmp/103/2/2026092900.024", 3, 2, 1);
    }

    @Test
    void contourEndpointReturnsGeoJson() throws Exception {
        when(contourService.contour(eq("cma-gfs/tmp/103/2/2026092900.024"), eq("0,10")))
                .thenReturn(Map.of(
                        "type", "FeatureCollection",
                        "features", List.of()));

        mockMvc.perform(get("/api/cma-nwp/contour")
                        .param("dataPath", "cma-gfs/tmp/103/2/2026092900.024")
                        .param("clevs", "0,10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isArray());
    }

    @Test
    void contourPathEndpointUsesConfiguredLevels() throws Exception {
        when(contourService.contour(eq("cma-gfs/hgt/100/500/2026092900.024"), eq(null)))
                .thenReturn(Map.of("type", "FeatureCollection", "features", List.of()));

        mockMvc.perform(get("/api/cma-nwp/contour/cma-gfs/hgt/100/500/2026092900.024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"));
        verify(contourService).contour("cma-gfs/hgt/100/500/2026092900.024", null);
    }

    @Test
    void messagePathEndpointPassesPathSelector() throws Exception {
        when(service.messageByTime("cma-gfs", "2t", 103, 2.0, "2026092900.024"))
                .thenReturn(Map.of("shortName", "2t"));

        mockMvc.perform(get("/api/cma-nwp/message/cma-gfs/2t/103/2/2026092900.024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortName").value("2t"));
    }

    @Test
    void processRainEndpointsPassBothSelectedTimes() throws Exception {
        String end = "apcp/1/0/2026092900.096";
        String start = "apcp/1/0/2026092900.039";
        String sourceEnd = "cma-gfs/" + end;
        String sourceStart = "cma-gfs/" + start;
        when(dataService.minusMeta(sourceEnd, sourceStart)).thenReturn(new NormalInfo());
        when(dataService.minusTile(sourceEnd, sourceStart, 0, 1, 0)).thenReturn(new byte[]{0, 25});
        mockMvc.perform(get("/api/cma-nwp/meteo/minusMeta")
                        .param("dataPath", sourceEnd).param("dataPath2", sourceStart))
                .andExpect(status().isOk());
        verify(dataService).minusMeta(sourceEnd, sourceStart);
        mockMvc.perform(get("/api/cma-nwp/meteo/minusTile")
                        .param("dataPath", sourceEnd).param("dataPath2", sourceStart)
                        .param("z", "0").param("x", "1").param("y", "0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/octet-stream"))
                .andExpect(content().bytes(new byte[]{0, 25}));
        mockMvc.perform(get("/api/cma-nwp/meteo/minusTile").param("dataPath", end)
                        .param("z", "0").param("x", "1").param("y", "0"))
                .andExpect(status().isBadRequest());
    }
}
