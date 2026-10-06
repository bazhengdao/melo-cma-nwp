package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataSource;
import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.mapping.Wgrib2NameMapping;
import com.meloxu.cmanwp.model.CmaNwpMessage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ucar.nc2.grib.grib2.Grib2Record;
import ucar.nc2.grib.grib2.Grib2RecordScanner;
import ucar.unidata.io.RandomAccessFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class CmaNwpService {
    /** GRIB2 level type 100: isobaric surface. The public API uses hPa. */
    private static final int ISOBARIC_LEVEL_TYPE = 100;
    private static final Pattern INITTIME = Pattern.compile("\\d{10}");
    private static final Pattern MESSAGE_TIME = Pattern.compile("(\\d{10})\\.(\\d{3})");
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter BEIJING_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private final CmaDataSourceRegistry sources;
    private final Wgrib2NameMapping names;
    private final CmaDataProperties properties;

    public CmaNwpService(CmaDataSourceRegistry sources, Wgrib2NameMapping names,
                         CmaDataProperties properties) {
        this.sources = sources;
        this.names = names;
        this.properties = properties;
    }

    public List<Map<String, Object>> initTimes() throws IOException {
        return initTimes("cma-gfs");
    }

    public List<Map<String, Object>> initTimes(String sourceId) throws IOException {
        Path root = source(sourceId).root();
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> inittime(path.getFileName().toString()))
                    .filter(value -> value != null)
                    .sorted(Comparator.comparing(value -> (String) value.get("utc"), Comparator.reverseOrder()))
                    .toList();
        }
    }

    public List<Map<String, Object>> validTimes(String sourceId, String inittime) throws IOException {
        return validTimes(sourceId, inittime, null);
    }

    public List<Map<String, Object>> validTimes(String sourceId, String inittime,
                                                String dataPath) throws IOException {
        if (!INITTIME.matcher(inittime).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inittime is invalid");
        }
        CmaDataSource source = source(sourceId);
        Path directory = source.root().resolve(inittime).normalize();
        if (!directory.startsWith(source.root()) || !Files.isDirectory(directory)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "init time directory not found");
        }
        String prefix = source.filePrefix() + inittime;
        Pattern forecastFile = Pattern.compile(Pattern.quote(prefix) + "(\\d{3})\\.grb2");
        try (Stream<Path> paths = Files.list(directory)) {
            Integer minVT = minVT(dataPath);
            return paths.filter(Files::isRegularFile)
                    .map(path -> validTime(path, forecastFile))
                    .filter(value -> value != null)
                    .filter(value -> minVT == null
                            || Integer.parseInt((String) value.get("validTime")) >= minVT)
                    .sorted(Comparator.comparingInt(value -> Integer.parseInt((String) value.get("validTime"))))
                    .toList();
        }
    }

    private Integer minVT(String dataPath) {
        if (dataPath == null || dataPath.isBlank()) return null;
        if (dataPath.split("/", -1).length != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dataPath must be shortName/levelType/level for validtimes");
        }
        String key = normalizeDataPath(dataPath);
        return properties.getDerived().stream()
                .filter(definition -> definition.getDataPath() != null
                        && normalizeDataPath(definition.getDataPath()).equalsIgnoreCase(key))
                .map(CmaDataProperties.Derived::getMinVT)
                .filter(value -> value > 0)
                .findFirst()
                .orElse(null);
    }

    private String normalizeDataPath(String dataPath) {
        String[] parts = dataPath.split("/", -1);
        if (parts.length >= 3) {
            return String.join("/", java.util.Arrays.copyOfRange(parts, 0, 3));
        }
        return dataPath;
    }

    private Map<String, Object> validTime(Path path, Pattern forecastFile) {
        Matcher matcher = forecastFile.matcher(path.getFileName().toString());
        if (!matcher.matches()) return null;
        String validTime = matcher.group(1);
        LocalDateTime init = LocalDateTime.parse(path.getParent().getFileName().toString(), UTC_FORMAT);
        LocalDateTime forecast = init.plusHours(Integer.parseInt(validTime));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("validTime", validTime);
        result.put("time", init.format(UTC_FORMAT) + "." + validTime);
        result.put("fcstTime", forecast.format(UTC_FORMAT));
        return result;
    }

    public Map<String, Object> message(String inittime, String forecast, String shortName,
                                       int levelType, double level) throws IOException {
        return message("cma-gfs", inittime, forecast, shortName, levelType, level);
    }

    public Map<String, Object> message(String sourceId, String inittime, String forecast,
                                       String shortName, int levelType, double level) throws IOException {
        CmaNwpMessage message = findMessage(sourceId, inittime, forecast, shortName, levelType, level);
        return serialize(message);
    }

    public CmaNwpMessage findMessage(String inittime, String forecast, String shortName,
                                      int levelType, double level) throws IOException {
        return findMessage("cma-gfs", inittime, forecast, shortName, levelType, level);
    }

    public CmaNwpMessage findMessage(String sourceId, String inittime, String forecast,
                                     String shortName, int levelType, double level) throws IOException {
        validateMessageRequest(inittime, forecast, shortName);
        Path file = file(sourceId, inittime, forecast);
        try (RandomAccessFile randomAccessFile = RandomAccessFile.acquire(file.toString())) {
            Grib2RecordScanner scanner = new Grib2RecordScanner(randomAccessFile);
            for (int index = 0; scanner.hasNext(); index++) {
                Grib2Record candidate = scanner.next();
                if (matches(candidate, shortName, levelType, level)) {
                    return new CmaNwpMessage(candidate, file, index, source(sourceId).id(), inittime, forecast,
                            shortName, levelType, level);
                }
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
        }
    }

    public CmaNwpMessage findLatestMessage(String shortName, int levelType, double level) throws IOException {
        return findLatestMessage("cma-gfs", shortName, levelType, level);
    }

    public CmaNwpMessage findLatestMessage(String sourceId, String shortName,
                                            int levelType, double level) throws IOException {
        CmaDataSource source = source(sourceId);
        Path dataRoot = source.root();
        if (!Files.isDirectory(dataRoot)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CMA-GFS data root not found");
        }
        try (Stream<Path> directories = Files.list(dataRoot)) {
            List<Path> runs = directories.filter(Files::isDirectory)
                    .filter(path -> INITTIME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
            for (Path run : runs) {
                String inittime = run.getFileName().toString();
                String prefix = source.filePrefix() + inittime;
                try (Stream<Path> files = Files.list(run)) {
                    List<Path> forecasts = files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().startsWith(prefix)
                                    && path.getFileName().toString().endsWith(".grb2"))
                            .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                            .toList();
                    for (Path forecastFile : forecasts) {
                        String name = forecastFile.getFileName().toString();
                        String forecast = name.substring(prefix.length(), prefix.length() + 3);
                        try {
                            return findMessage(sourceId, inittime, forecast, shortName, levelType, level);
                        } catch (ResponseStatusException notFound) {
                            if (notFound.getStatusCode() != HttpStatus.NOT_FOUND) throw notFound;
                        }
                    }
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message not found");
    }

    public Map<String, Object> messageByTime(String shortName, int levelType, double level,
                                             String time) throws IOException {
        return messageByTime("cma-gfs", shortName, levelType, level, time);
    }

    public Map<String, Object> messageByTime(String sourceId, String shortName, int levelType,
                                             double level, String time) throws IOException {
        Matcher matcher = MESSAGE_TIME.matcher(time);
        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "time must be yyyyMMddHH.FFF");
        }
        return message(sourceId, matcher.group(1), matcher.group(2), shortName, levelType, level);
    }

    public Map<String, Object> messageCatalog(String inittime, String forecast) throws IOException {
        return messageCatalog("cma-gfs", inittime, forecast);
    }

    public Map<String, Object> messageCatalog(String sourceId, String inittime,
                                              String forecast) throws IOException {
        Path file = file(sourceId, inittime, forecast);
        List<Map<String, Object>> messages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (RandomAccessFile randomAccessFile = RandomAccessFile.acquire(file.toString())) {
            Grib2RecordScanner scanner = new Grib2RecordScanner(randomAccessFile);
            for (int index = 0; scanner.hasNext(); index++) {
                Grib2Record record = scanner.next();
                int discipline = record.getDiscipline();
                int category = record.getPDS().getParameterCategory();
                int parameter = record.getPDS().getParameterNumber();
                int levelType = record.getPDS().getLevelType1();
                double level = normalizedLevel(record);
                String key = discipline + ":" + category + ":" + parameter + ":" + levelType + ":" + level;
                if (seen.add(key)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("discipline", discipline);
                    item.put("category", category);
                    item.put("parameter", parameter);
                    item.put("levelType", levelType);
                    item.put("level", level);
                    item.put("shortName", shortName(record));
                    item.put("message", index);
                    messages.add(item);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source(sourceId).id());
        result.put("inittime", inittime);
        result.put("forecast", forecast);
        result.put("file", file.getFileName().toString());
        result.put("count", messages.size());
        result.put("messages", messages);
        return result;
    }

    private void validateMessageRequest(String inittime, String forecast, String shortName) {
        if (!INITTIME.matcher(inittime).matches() || !forecast.matches("\\d{3}") || shortName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "inittime, forecast or message selector is invalid");
        }
    }

    private boolean matches(Grib2Record record, String shortName, int levelType, double level) {
        return shortName.equalsIgnoreCase(shortName(record))
                && record.getPDS().getLevelType1() == levelType
                && Math.abs(normalizedLevel(record) - level) < 1e-6;
    }

    private String shortName(Grib2Record record) {
        int discipline = record.getDiscipline();
        int category = record.getPDS().getParameterCategory();
        int parameter = record.getPDS().getParameterNumber();
        String key = discipline + ":" + category + ":" + parameter;
        return names.resolve(key);
    }

    private double normalizedLevel(Grib2Record record) {
        return normalizeLevel(record.getPDS().getLevelType1(), record.getPDS().getLevelValue1());
    }

    /**
     * Normalizes pressure levels to the hPa unit used by selectors and responses.
     * Some GRIB readers/data files expose isobaric levels in Pa while others
     * already expose hPa, so only the unambiguous Pa range is converted.
     */
    static double normalizeLevel(int levelType, double level) {
        if (!Double.isFinite(level) || Math.abs(level) > 1.0e15) return 0;
        if (levelType == ISOBARIC_LEVEL_TYPE && Math.abs(level) > 2_000) {
            return level / 100.0;
        }
        return level;
    }

    private Map<String, Object> serialize(CmaNwpMessage message) {
        Grib2Record record = message.record();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inittime", message.inittime());
        result.put("source", message.source());
        result.put("forecast", message.forecast());
        result.put("message", message.index());
        result.put("shortName", message.shortName());
        result.put("levelType", message.levelType());
        result.put("level", message.level());
        result.put("file", message.file().getFileName().toString());
        result.put("discipline", record.getDiscipline());
        result.put("center", record.getId().getCenter_id());
        result.put("subCenter", record.getId().getSubcenter_id());
        result.put("referenceTime", record.getReferenceDate());
        result.put("productDefinition", record.getPDS().getTemplateNumber());
        result.put("parameterCategory", record.getPDS().getParameterCategory());
        result.put("parameterNumber", record.getPDS().getParameterNumber());
        result.put("matchedLevelType", record.getPDS().getLevelType1());
        result.put("matchedLevel", normalizedLevel(record));
        result.put("dataRepresentation", record.getGDS().getClass().getSimpleName());
        result.put("grid", record.getGDS().toString());
        return result;
    }

    private Map<String, Object> inittime(String value) {
        Matcher matcher = INITTIME.matcher(value);
        if (!matcher.matches()) return null;
        LocalDateTime utc = LocalDateTime.parse(value, UTC_FORMAT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("utc", value);
        result.put("beijing", utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(BEIJING).format(BEIJING_FORMAT));
        result.put("directory", value);
        return result;
    }

    private CmaDataSource source(String sourceId) {
        return sources.get(sourceId);
    }

    private Path file(String sourceId, String inittime, String forecast) {
        if (!INITTIME.matcher(inittime).matches() || !forecast.matches("\\d{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inittime or forecast is invalid");
        }
        CmaDataSource source = source(sourceId);
        Path file = source.file(inittime, forecast);
        if (!file.startsWith(source.root()) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GRIB2 file not found");
        }
        return file;
    }
}
