package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.model.NormalData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CmaNwpContourService {
    private final CmaNwpDataService dataService;
    private final CmaDataProperties properties;
    private final CmaDataSourceRegistry sources;
    private final CmaNwpContourCache cache;

    public CmaNwpContourService(CmaNwpDataService dataService, CmaDataProperties properties,
                                CmaDataSourceRegistry sources, CmaNwpContourCache cache) {
        this.dataService = dataService;
        this.properties = properties;
        this.sources = sources;
        this.cache = cache;
    }

    public Map<String, Object> contour(String dataPath, String clevs) throws IOException {
        double[] levels = levels(dataPath, clevs);
        CmaNwpContourCache.Key key = new CmaNwpContourCache.Key(dataPath, Arrays.stream(levels).boxed().toList());
        Map<String, Object> cached = cache.get(key);
        if (cached != null) return cached;
        NormalData data = dataService.normalDataForPath(dataPath);
        Map<String, Object> result = contourLines(data, levels);
        cache.put(key, result);
        return result;
    }

    private double[] levels(String dataPath, String clevs) {
        String configured = configuredLevels(dataPath);
        String value = clevs == null || clevs.isBlank() ? configured : clevs;
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "clevs is required when no contour levels are configured for dataPath");
        }
        String[] tokens = value.split(",");
        double[] levels = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                levels[i] = Double.parseDouble(tokens[i].trim());
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "clevs must be comma-separated numbers", exception);
            }
            if (!Double.isFinite(levels[i])) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clevs must be finite numbers");
            }
        }
        Arrays.sort(levels);
        int unique = 0;
        for (double level : levels) {
            if (unique == 0 || Double.compare(level, levels[unique - 1]) != 0) {
                levels[unique++] = level;
            }
        }
        return Arrays.copyOf(levels, unique);
    }

    private String configuredLevels(String dataPath) {
        String key = normalizeDataPath(dataPath);
        for (CmaDataProperties.Contour contour : properties.getContours()) {
            if (contour.getDataPath() != null
                    && normalizeDataPath(contour.getDataPath()).equalsIgnoreCase(key)) {
                List<Double> values = contour.getClevs();
                if (values == null || values.isEmpty()) {
                    return null;
                }
                return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(null);
            }
        }
        return null;
    }

    private String normalizeDataPath(String dataPath) {
        String[] parts = dataPath.split("/", -1);
        if (parts.length >= 4 && sources.contains(parts[0])) {
            return String.join("/", Arrays.copyOfRange(parts, 1, 4));
        }
        if (parts.length >= 4) {
            return String.join("/", Arrays.copyOfRange(parts, 0, 3));
        }
        return dataPath;
    }

    private Map<String, Object> contourLines(NormalData normal, double[] levels) {
        List<Map<String, Object>> features = new ArrayList<>();
        for (double level : levels) {
            List<Line> lines = linesForLevel(normal, level);
            for (Line line : lines) {
                if (line.points.size() < 2) {
                    continue;
                }
                Map<String, Object> geometry = new LinkedHashMap<>();
                geometry.put("type", "LineString");
                geometry.put("coordinates", line.points.stream()
                        .map(point -> point.stream()
                                .map(value -> Math.round(value * 10_000d) / 10_000d).toList())
                        .toList());
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("level", level);
                Map<String, Object> feature = new LinkedHashMap<>();
                feature.put("type", "Feature");
                feature.put("properties", properties);
                feature.put("geometry", geometry);
                features.add(feature);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FeatureCollection");
        result.put("features", features);
        return result;
    }

    private List<Line> linesForLevel(NormalData normal, double level) {
        if (normal.data == null || normal.data.length < 2 || normal.data[0].length < 2) {
            return Collections.emptyList();
        }
        List<Line> lines = new ArrayList<>();
        Map<PointKey, Line> endpoints = new HashMap<>();
        int height = normal.data.length;
        int width = normal.data[0].length;
        float south = normal.bounds._southWest.lat;
        float west = normal.bounds._southWest.lng;
        for (int row = 0; row < height - 1; row++) {
            for (int column = 0; column < width - 1; column++) {
                double sw = normal.data[row][column];
                double se = normal.data[row][column + 1];
                double ne = normal.data[row + 1][column + 1];
                double nw = normal.data[row + 1][column];
                if (!valid(sw) || !valid(se) || !valid(ne) || !valid(nw)) {
                    continue;
                }
                int mask = (sw >= level ? 1 : 0) | (se >= level ? 2 : 0)
                        | (ne >= level ? 4 : 0) | (nw >= level ? 8 : 0);
                int[][] pairs = CASES[mask];
                if (pairs == null) {
                    continue;
                }
                Point[] edgePoints = new Point[]{
                        edge(sw, se, column, row, column + 1, row, west, south, normal.res, level),
                        edge(se, ne, column + 1, row, column + 1, row + 1, west, south, normal.res, level),
                        edge(nw, ne, column, row + 1, column + 1, row + 1, west, south, normal.res, level),
                        edge(sw, nw, column, row, column, row + 1, west, south, normal.res, level)
                };
                for (int[] pair : pairs) {
                    addSegment(lines, endpoints, edgePoints[pair[0]], edgePoints[pair[1]]);
                }
            }
        }
        return lines;
    }

    private static final int[][][] CASES = new int[][][]{
            null, {{3, 0}}, {{0, 1}}, {{3, 1}}, {{1, 2}}, {{3, 2}, {0, 1}},
            {{0, 2}}, {{3, 2}}, {{2, 3}}, {{0, 2}}, {{0, 1}, {2, 3}}, {{1, 2}},
            {{1, 3}}, {{0, 1}}, {{3, 0}}, null
    };

    private static boolean valid(double value) {
        return Double.isFinite(value) && Math.abs(value) < 1.0e30;
    }

    private static Point edge(double first, double second, int x1, int y1, int x2, int y2,
                              float west, float south, float resolution, double level) {
        double fraction = second == first ? 0.5 : (level - first) / (second - first);
        fraction = Math.max(0, Math.min(1, fraction));
        return new Point(west + (x1 + (x2 - x1) * fraction) * resolution,
                south + (y1 + (y2 - y1) * fraction) * resolution);
    }

    private static void addSegment(List<Line> lines, Map<PointKey, Line> endpoints, Point first, Point second) {
        PointKey firstKey = PointKey.of(first);
        PointKey secondKey = PointKey.of(second);
        Line firstLine = endpoints.get(firstKey);
        Line secondLine = endpoints.get(secondKey);
        if (firstLine == null && secondLine == null) {
            Line line = new Line();
            line.points.add(first.coordinates());
            line.points.add(second.coordinates());
            lines.add(line);
            endpoints.put(firstKey, line);
            endpoints.put(secondKey, line);
        } else if (firstLine == null) {
            prependOrAppend(secondLine, secondKey, first);
            endpoints.put(firstKey, secondLine);
        } else if (secondLine == null) {
            prependOrAppend(firstLine, firstKey, second);
            endpoints.put(secondKey, firstLine);
        } else if (firstLine != secondLine) {
            merge(lines, endpoints, firstLine, firstKey, secondLine, secondKey);
        }
    }

    private static void prependOrAppend(Line line, PointKey endpoint, Point point) {
        PointKey start = PointKey.of(line.points.get(0));
        if (start.equals(endpoint)) {
            line.points.add(0, point.coordinates());
        } else {
            line.points.add(point.coordinates());
        }
    }

    private static void merge(List<Line> lines, Map<PointKey, Line> endpoints,
                              Line left, PointKey leftKey, Line right, PointKey rightKey) {
        boolean leftAtStart = PointKey.of(left.points.get(0)).equals(leftKey);
        boolean rightAtStart = PointKey.of(right.points.get(0)).equals(rightKey);
        if (leftAtStart) {
            Collections.reverse(left.points);
        }
        if (!rightAtStart) {
            Collections.reverse(right.points);
        }
        left.points.addAll(right.points);
        lines.remove(right);
        endpoints.values().removeIf(value -> value == right);
        endpoints.put(PointKey.of(left.points.get(0)), left);
        endpoints.put(PointKey.of(left.points.get(left.points.size() - 1)), left);
    }

    private static final class Line {
        private final List<List<Double>> points = new ArrayList<>();
    }

    private record Point(double lng, double lat) {
        List<Double> coordinates() {
            return List.of(lng, lat);
        }
    }

    private record PointKey(long lng, long lat) {
        static PointKey of(Point point) {
            return new PointKey(Math.round(point.lng * 1_000_000), Math.round(point.lat * 1_000_000));
        }

        static PointKey of(List<Double> point) {
            return new PointKey(Math.round(point.get(0) * 1_000_000), Math.round(point.get(1) * 1_000_000));
        }
    }
}
