package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.model.CmaNwpMessage;
import com.meloxu.cmanwp.model.LatLng;
import com.meloxu.cmanwp.model.LatLngBounds;
import com.meloxu.cmanwp.model.NormalData;
import com.meloxu.cmanwp.model.NormalInfo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ucar.nc2.grib.GdsHorizCoordSys;
import ucar.unidata.io.RandomAccessFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CmaNwpDataService {
    private static final Pattern MESSAGE_TIME = Pattern.compile("(\\d{10})\\.(\\d{3})");

    private final CmaNwpService messages;
    private final CmaNwpTileService tiles;
    private final CmaDataSourceRegistry sources;
    private final CmaNwpDerivedService derived;
    private final CmaNwpUnitService units;
    private final CmaNwpNormalDataCache cache;

    public CmaNwpDataService(CmaNwpService messages, CmaNwpTileService tiles,
                             CmaDataSourceRegistry sources, CmaNwpDerivedService derived,
                             CmaNwpUnitService units, CmaNwpNormalDataCache cache) {
        this.messages = messages;
        this.tiles = tiles;
        this.sources = sources;
        this.derived = derived;
        this.units = units;
        this.cache = cache;
    }

    public NormalData normalData(String sourceId, String inittime, String forecast,
                                 String shortName, int levelType, double level) throws IOException {
        CmaNwpNormalDataCache.Key key = key(sourceId, inittime, forecast, shortName, levelType, level);
        NormalData cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        NormalData loaded = derived.contains(shortName, levelType, level)
                ? derived.normalData(sourceId, inittime, forecast, shortName,
                levelType, level, this::normalData)
                : rawNormalData(sourceId, inittime, forecast, shortName, levelType, level);
        cache.put(key, loaded);
        return loaded;
    }

    private NormalData rawNormalData(String sourceId, String inittime, String forecast,
                                     String shortName, int levelType, double level) throws IOException {
        return normalData(messages.findMessage(sourceId, inittime, forecast,
                shortName, levelType, level));
    }

    private NormalData normalData(CmaNwpMessage message) throws IOException {
        try (RandomAccessFile randomAccessFile = RandomAccessFile.acquire(message.file().toString())) {
            GdsHorizCoordSys grid = message.record().getGDS().makeHorizCoordSys();
            if (!grid.isLatLon()) {
                throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                        "only latitude-longitude GRIB2 grids are supported");
            }
            NormalData normal = normalize(message.record().readData(randomAccessFile), grid);
            units.apply(normal, message.shortName(), message.levelType(), message.level());
            return normal;
        }
    }

    public NormalInfo meta(String dataPath) throws IOException {
        TileRequest request = parseDataPath(dataPath, false);
        NormalData normal = request.inittime() == null
                ? latestNormalData(request)
                : normalData(request.sourceId(), request.inittime(), request.forecast(), request.shortName(),
                request.levelType(), request.level());
        return tiles.metadata(normal);
    }

    public NormalData normalDataForPath(String dataPath) throws IOException {
        TileRequest request = parseDataPath(dataPath, false);
        return request.inittime() == null
                ? latestNormalData(request)
                : normalData(request.sourceId(), request.inittime(), request.forecast(), request.shortName(),
                request.levelType(), request.level());
    }

    public byte[] tile(String dataPath, int z, int x, int y) throws IOException {
        TileRequest request = parseDataPath(dataPath, true);
        if (z < 0 || z > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "z must be between 0 and 12");
        }
        NormalData normal = normalData(request.sourceId(), request.inittime(), request.forecast(),
                request.shortName(), request.levelType(), request.level());
        return tiles.tile(normal, z, x, y);
    }

    private NormalData latestNormalData(TileRequest request) throws IOException {
        if (derived.contains(request.shortName(), request.levelType(), request.level())) {
            CmaNwpNormalDataCache.Key key = key(request.sourceId(), null, null, request.shortName(),
                    request.levelType(), request.level());
            NormalData cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            NormalData loaded = derived.latestNormalData(request.sourceId(), request.shortName(),
                    request.levelType(), request.level(), this::normalData);
            cache.put(key, loaded);
            return loaded;
        }
        CmaNwpMessage message = messages.findLatestMessage(request.sourceId(), request.shortName(),
                request.levelType(), request.level());
        CmaNwpNormalDataCache.Key key = key(request.sourceId(), message.inittime(), message.forecast(),
                request.shortName(), request.levelType(), request.level());
        NormalData cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        NormalData loaded = normalData(message);
        cache.put(key, loaded);
        return loaded;
    }

    public NormalInfo minusMeta(String dataPath, String dataPath2) throws IOException {
        return tiles.metadata(minusData(dataPath, dataPath2));
    }

    public byte[] minusTile(String dataPath, String dataPath2, int z, int x, int y) throws IOException {
        if (z < 0 || z > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "z must be between 0 and 12");
        }
        return tiles.tile(minusData(dataPath, dataPath2), z, x, y);
    }

    /**
     * Subtracts two compatible fields from the same model run.  The endpoint is
     * intentionally generic: APCP process rainfall is one caller, but the same
     * operation is also useful for temperature, pressure and other fields.
     */
    NormalData minusData(String dataPath, String dataPath2) throws IOException {
        TileRequest end = parseDataPath(dataPath, true);
        TileRequest start = parseDataPath(dataPath2, true);
        if (!end.sourceId().equals(start.sourceId()) || !end.inittime().equals(start.inittime())
                || !end.shortName().equalsIgnoreCase(start.shortName())
                || end.levelType() != start.levelType() || end.level() != start.level()
                || Integer.parseInt(start.forecast()) >= Integer.parseInt(end.forecast())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minus requires two fields of the same variable and level from the same run, with start < end");
        }
        // Include both endpoints so changing either input cannot reuse the previous result.
        CmaNwpNormalDataCache.Key processKey = key(end.sourceId(), end.inittime(),
                end.forecast() + "-" + start.forecast(), "minus:" + end.shortName(), end.levelType(), end.level());
        NormalData cached = cache.get(processKey);
        if (cached != null) return cached;
        NormalData current = normalData(end.sourceId(), end.inittime(), end.forecast(), end.shortName(), end.levelType(), end.level());
        NormalData previous = normalData(start.sourceId(), start.inittime(), start.forecast(), start.shortName(), start.levelType(), start.level());
        if (current.res != previous.res || current.data.length != previous.data.length
                || current.bounds._southWest.lat != previous.bounds._southWest.lat
                || current.bounds._southWest.lng != previous.bounds._southWest.lng
                || current.bounds._northEast.lat != previous.bounds._northEast.lat
                || current.bounds._northEast.lng != previous.bounds._northEast.lng) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "APCP grids do not match");
        }
        NormalData result = new NormalData();
        result.res = current.res;
        result.bounds = current.bounds;
        result.unit = current.unit;
        result.data = new float[current.data.length][];
        for (int row = 0; row < current.data.length; row++) {
            if (current.data[row].length != previous.data[row].length) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "APCP grids do not match");
            }
            result.data[row] = new float[current.data[row].length];
            for (int column = 0; column < current.data[row].length; column++) {
                float last = current.data[row][column];
                float first = previous.data[row][column];
                result.data[row][column] = Float.isFinite(last) && Float.isFinite(first)
                        ? last - first : Float.NaN;
            }
        }
        cache.put(processKey, result);
        return result;
    }

    private CmaNwpNormalDataCache.Key key(String sourceId, String inittime, String forecast,
                                          String shortName, int levelType, double level) {
        return new CmaNwpNormalDataCache.Key(CmaDataSourceRegistry.normalize(sourceId), inittime,
                forecast, shortName.toLowerCase(java.util.Locale.ROOT), levelType, level);
    }

    private TileRequest parseDataPath(String dataPath, boolean timeRequired) {
        if (dataPath == null || dataPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dataPath must be shortName/levelType/level/inittime.forecast");
        }
        String[] parts = dataPath.split("/", -1);
        if (parts.length == 0 || !sources.contains(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dataPath must start with a configured dataCode");
        }
        String sourceId = CmaDataSourceRegistry.normalize(parts[0]);
        parts = Arrays.copyOfRange(parts, 1, parts.length);
        if ((parts.length != 3 && parts.length != 4) || parts[0].isBlank()
                || parts[1].isBlank() || parts[2].isBlank() || (timeRequired && parts.length != 4)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    timeRequired
                            ? "dataPath must be shortName/levelType/level/inittime.forecast"
                            : "dataPath must be shortName/levelType/level[/inittime.forecast]");
        }
        int levelType;
        double level;
        try {
            levelType = Integer.parseInt(parts[1]);
            level = Double.parseDouble(parts[2]);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dataPath levelType and level are invalid", exception);
        }
        if (parts.length == 3) {
            return new TileRequest(sourceId, parts[0], levelType, level, null, null);
        }
        Matcher time = MESSAGE_TIME.matcher(parts[3]);
        if (!time.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataPath time must be yyyyMMddHH.FFF");
        }
        return new TileRequest(sourceId, parts[0], levelType, level, time.group(1), time.group(2));
    }

    private NormalData normalize(float[] values, GdsHorizCoordSys grid) {
        int width = grid.nx;
        int height = grid.ny;
        if (values.length < width * height) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GRIB2 data size does not match its grid");
        }
        double resolutionX = Math.abs(grid.dx);
        double resolutionY = Math.abs(grid.dy);
        if (Math.abs(width * resolutionX - 360.0) > 1e-3
                || Math.abs(height * resolutionY - 180.0) > 1e-3) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "only global regular latitude-longitude grids are supported");
        }
        int shift = Math.floorMod((int) Math.round((-180.0 - grid.startx) / grid.dx), width);
        float[][] data = new float[height][width];
        for (int row = 0; row < height; row++) {
            int sourceRow = grid.dy < 0 ? height - 1 - row : row;
            for (int column = 0; column < width; column++) {
                int sourceColumn = Math.floorMod(column + shift, width);
                data[row][column] = values[sourceRow * width + sourceColumn];
            }
        }
        NormalData normal = new NormalData();
        normal.res = (float) resolutionX;
        normal.bounds = new LatLngBounds(new LatLng(-90, -180), new LatLng(90, 180));
        normal.data = data;
        return normal;
    }

    private record TileRequest(String sourceId, String shortName, int levelType, double level,
                               String inittime, String forecast) {
    }
}
