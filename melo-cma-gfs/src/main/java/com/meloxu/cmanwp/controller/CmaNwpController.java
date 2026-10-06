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
            @RequestParam String inittime,
            @RequestParam(required = false) String dataPath) throws IOException {
        return dataPath == null || dataPath.isBlank()
                ? service.validTimes("cma-gfs", inittime)
                : service.validTimes(sourceFromPath(dataPath), inittime, validTimePath(relativePath(dataPath)));
    }

    @GetMapping("/messages/catalog")
    public Map<String, Object> messageCatalog(@RequestParam String inittime,
                                               @RequestParam String forecast,
                                               @RequestParam(name = "dataCode", defaultValue = "cma-gfs") String dataCode) throws IOException {
        return service.messageCatalog(dataCode, inittime, forecast);
    }

    @GetMapping("/meta")
    public NormalInfo meta(@RequestParam String dataPath) throws IOException {
        return dataService.meta(dataPath);
    }

    @GetMapping(value = "/tile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> tile(@RequestParam String dataPath,
                                       @RequestParam int z,
                                       @RequestParam int x,
                                       @RequestParam int y) throws IOException {
        return tileResponse(dataPath, z, x, y);
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
        return tileResponse(dataCode + "/" + dataPath, z, x, y);
    }

    @GetMapping("/contour")
    public Map<String, Object> contour(@RequestParam String dataPath,
                                       @RequestParam(required = false) String clevs) throws IOException {
        return contourService.contour(dataPath, clevs);
    }

    @GetMapping("/contour/{dataCode}/{shortName}/{levelType}/{level}/{time}")
    public Map<String, Object> contourPath(@PathVariable String dataCode,
                                           @PathVariable String shortName,
                                           @PathVariable int levelType,
                                           @PathVariable String level,
                                           @PathVariable String time) throws IOException {
        String dataPath = String.join("/", shortName, Integer.toString(levelType), level, time);
        return contourService.contour(dataCode + "/" + dataPath, null);
    }

    @GetMapping("/meteo/minusMeta")
    public NormalInfo minusMeta(@RequestParam String dataPath,
                                @RequestParam String dataPath2) throws IOException {
        return dataService.minusMeta(dataPath, dataPath2);
    }

    @GetMapping(value = "/meteo/minusTile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> minusTile(@RequestParam String dataPath,
                                            @RequestParam String dataPath2,
                                            @RequestParam int z,
                                            @RequestParam int x,
                                            @RequestParam int y) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(dataService.minusTile(dataPath, dataPath2, z, x, y));
    }

    private String sourceFromPath(String dataPath) {
        if (dataPath == null || dataPath.isBlank()) return "cma-gfs";
        int separator = dataPath.indexOf('/');
        if (separator < 0) return dataPath;
        if (separator == 0 || separator == dataPath.length() - 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "dataPath must start with dataCode/");
        }
        return dataPath.substring(0, separator);
    }

    private String relativePath(String dataPath) {
        int separator = dataPath.indexOf('/');
        return dataPath.substring(separator + 1);
    }

    private String validTimePath(String dataPath) {
        String[] parts = dataPath.split("/", -1);
        if (parts.length < 3) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "dataPath must be dataCode/shortName/levelType/level");
        }
        return String.join("/", java.util.Arrays.copyOf(parts, 3));
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
