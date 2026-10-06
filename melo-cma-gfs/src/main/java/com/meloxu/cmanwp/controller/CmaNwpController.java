package com.meloxu.cmanwp.controller;

import com.meloxu.cmanwp.model.NormalInfo;
import com.meloxu.cmanwp.service.CmaNwpDataService;
import com.meloxu.cmanwp.service.CmaNwpContourService;
import com.meloxu.cmanwp.service.CmaNwpService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cma-nwp")
public class CmaNwpController {
    private final CmaNwpService service;
    private final CmaNwpDataService dataService;
    private final CmaNwpContourService contourService;

    public CmaNwpController(CmaNwpService service, CmaNwpDataService dataService,
                            CmaNwpContourService contourService) {
        this.service = service;
        this.dataService = dataService;
        this.contourService = contourService;
    }

    @GetMapping("/inittimes")
    public List<Map<String, Object>> initTimes(
            @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode) throws IOException {
        return service.initTimes(dataCode);
    }
    
    @GetMapping("/validtimes")
    public List<Map<String, Object>> validTimes(
            @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode,
            @RequestParam String inittime,
            @RequestParam(required = false) String dataPath) throws IOException {
        return dataPath == null || dataPath.isBlank()
                ? service.validTimes(dataCode, inittime)
                : service.validTimes(dataCode, inittime, dataPath);
    }

    @GetMapping("/messages")
    public Map<String, Object> message(@RequestParam String inittime,
                                       @RequestParam String forecast,
                                       @RequestParam String shortName,
                                       @RequestParam int levelType,
                                       @RequestParam double level,
                                       @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode) throws IOException {
        return service.message(dataCode, inittime, forecast, shortName, levelType, level);
    }

    @GetMapping("/messages/catalog")
    public Map<String, Object> messageCatalog(@RequestParam String inittime,
                                               @RequestParam String forecast,
                                               @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode) throws IOException {
        return service.messageCatalog(dataCode, inittime, forecast);
    }

    @GetMapping("/meta")
    public NormalInfo meta(@RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode,
                           @RequestParam String dataPath) throws IOException {
        return dataService.meta(withDataCode(dataCode, dataPath));
    }

    @GetMapping(value = "/tile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> tile(@RequestParam String dataPath,
                                       @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode,
                                       @RequestParam int z,
                                       @RequestParam int x,
                                       @RequestParam int y) throws IOException {
        return tileResponse(withDataCode(dataCode, dataPath), z, x, y);
    }

    @GetMapping(value = "/tile/{dataCode}/{shortName}/{levelType}/{level}/{time}/{z}/{x}/{y}.bin",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> tilePath(@PathVariable String dataCode,
                                           @PathVariable String shortName,
                                           @PathVariable int levelType,
                                           @PathVariable String level,
                                           @PathVariable String time,
                                           @PathVariable int z,
                                           @PathVariable int x,
                                           @PathVariable int y) throws IOException {
        String dataPath = String.join("/", shortName, Integer.toString(levelType), level, time);
        return tileResponse(withDataCode(dataCode, dataPath), z, x, y);
    }

    @GetMapping("/contour")
    public Map<String, Object> contour(@RequestParam String dataPath,
                                       @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode,
                                       @RequestParam(required = false) String clevs) throws IOException {
        return contourService.contour(withDataCode(dataCode, dataPath), clevs);
    }

    @GetMapping("/contour/{dataCode}/{shortName}/{levelType}/{level}/{time}")
    public Map<String, Object> contourPath(@PathVariable String dataCode,
                                           @PathVariable String shortName,
                                           @PathVariable int levelType,
                                           @PathVariable String level,
                                           @PathVariable String time) throws IOException {
        String dataPath = String.join("/", shortName, Integer.toString(levelType), level, time);
        return contourService.contour(withDataCode(dataCode, dataPath), null);
    }

    @GetMapping("/meteo/minusMeta")
    public NormalInfo minusMeta(@RequestParam String dataPath,
                                @RequestParam String dataPath2,
                                @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode) throws IOException {
        return dataService.minusMeta(withDataCode(dataCode, dataPath), withDataCode(dataCode, dataPath2));
    }

    @GetMapping(value = "/meteo/minusTile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> minusTile(@RequestParam String dataPath,
                                            @RequestParam String dataPath2,
                                            @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode,
                                            @RequestParam int z,
                                            @RequestParam int x,
                                            @RequestParam int y) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataService.minusTile(withDataCode(dataCode, dataPath), withDataCode(dataCode, dataPath2), z, x, y));
    }

    private String withDataCode(String dataCode, String dataPath) {
        String source = dataCode == null || dataCode.isBlank() ? "cma-gfs" : dataCode.trim();
        if (dataPath != null && dataPath.regionMatches(true, 0, source + "/", 0, source.length() + 1)) {
            return dataPath;
        }
        return source + "/" + dataPath;
    }

    private ResponseEntity<byte[]> tileResponse(String dataPath, int z, int x, int y) throws IOException {
        byte[] body = dataService.tile(dataPath, z, x, y);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(body);
    }

    @GetMapping("/message/{dataCode}/{shortName}/{levelType}/{level}/{time}")
    public Map<String, Object> messageByPath(@PathVariable String dataCode,
                                              @PathVariable String shortName,
                                              @PathVariable int levelType,
                                              @PathVariable double level,
                                              @PathVariable String time) throws IOException {
        return service.messageByTime(dataCode, shortName, levelType, level, time);
    }
}
