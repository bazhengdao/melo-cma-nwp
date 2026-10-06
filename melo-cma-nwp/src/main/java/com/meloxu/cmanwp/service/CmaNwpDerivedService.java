package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.config.CmaDataSourceRegistry;
import com.meloxu.cmanwp.model.CmaNwpMessage;
import com.meloxu.cmanwp.model.NormalData;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CmaNwpDerivedService {
    private final CmaNwpService messages;
    private final CmaDataSourceRegistry sources;
    private final Map<String, DerivedSpec> definitions = new LinkedHashMap<>();

    public CmaNwpDerivedService(CmaNwpService messages, CmaDataSourceRegistry sources,
                                CmaDataProperties properties) {
        this.messages = messages;
        this.sources = sources;
        properties.getDerived().forEach(definition -> {
            DerivedSpec spec = parse(definition.getDataPath(), definition);
            definitions.put(key(spec.name(), spec.levelType(), spec.level()), spec);
        });
    }

    public boolean contains(String shortName, int levelType, double level) {
        return definitions.containsKey(key(shortName, levelType, level));
    }

    private DerivedSpec definition(String shortName, int levelType, double level) {
        DerivedSpec definition = definitions.get(key(shortName, levelType, level));
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "derived data not found: " + shortName + "/" + levelType + "/" + level);
        }
        return definition;
    }

    public NormalData normalData(String sourceId, String inittime, String forecast,
                                 String shortName, int levelType, double level,
                                 NormalDataLoader loader) throws IOException {
        DerivedSpec definition = definition(shortName, levelType, level);
        return calculate(sourceId, inittime, forecast, definition, loader);
    }

    public NormalData latestNormalData(String sourceId, String shortName, int levelType,
                                       double level, NormalDataLoader loader) throws IOException {
        DerivedSpec definition = definition(shortName, levelType, level);
        String referenceSource = definition.sourceId() == null ? sourceId : definition.sourceId();
        CmaNwpMessage message = latestRawMessage(referenceSource, definition);
        return calculate(referenceSource, message.inittime(), message.forecast(), definition, loader);
    }

    private NormalData calculate(String sourceId, String inittime, String forecast,
                                 DerivedSpec definition, NormalDataLoader loader) throws IOException {
        sourceId = definition.sourceId() == null ? sourceId : definition.sourceId();
        int hour = Integer.parseInt(forecast);
        int firstHour = definition.minVT();
        if (hour < firstHour) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    definition.name() + " is unavailable before forecast hour " + firstHour);
        }
        if (!definition.isDifference()) {
            List<NormalData> values = new ArrayList<>(definition.count());
            for (int offset = 0; offset < definition.count(); offset++) {
                int valueHour = hour - offset * definition.step();
                if (valueHour < 0) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            definition.name() + " has no complete aggregation window at forecast " + forecast);
                }
                values.add(loader.load(sourceId, inittime, String.format("%03d", valueHour),
                        definition.refName(), definition.refLevelType(), definition.refLevel()));
            }
            return aggregate(values, definition.aggr());
        }
        NormalData current = loader.load(sourceId, inittime, forecast, definition.refName(),
                definition.refLevelType(), definition.refLevel());
        NormalData previous = loader.load(sourceId, inittime,
                String.format("%03d", hour - definition.step()), definition.refName(),
                definition.refLevelType(), definition.refLevel());
        return subtract(current, previous);
    }

    private CmaNwpMessage latestRawMessage(String sourceId, DerivedSpec definition) throws IOException {
        DerivedSpec reference = definitions.get(key(definition.refName(),
                definition.refLevelType(), definition.refLevel()));
        if (reference == null) {
            return messages.findLatestMessage(sourceId, definition.refName(),
                    definition.refLevelType(), definition.refLevel());
        }
        String referenceSource = reference.sourceId() == null ? sourceId : reference.sourceId();
        return latestRawMessage(referenceSource, reference);
    }

    private NormalData aggregate(List<NormalData> values, String aggr) {
        NormalData result = copy(values.get(0));
        for (int row = 0; row < result.data.length; row++) {
            for (int column = 0; column < result.data[row].length; column++) {
                float value = result.data[row][column];
                for (int index = 1; index < values.size(); index++) {
                    float next = values.get(index).data[row][column];
                    value = switch (aggr) {
                        case "avg" -> value + next;
                        case "max" -> Math.max(value, next);
                        case "min" -> Math.min(value, next);
                        default -> throw new IllegalStateException("unsupported aggregation: " + aggr);
                    };
                }
                result.data[row][column] = "avg".equals(aggr) ? value / values.size() : value;
            }
        }
        return result;
    }

    private NormalData subtract(NormalData current, NormalData previous) {
        if (current.data.length != previous.data.length
                || current.data[0].length != previous.data[0].length) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "derived data grids do not have the same shape");
        }
        NormalData result = copy(current);
        for (int row = 0; row < result.data.length; row++) {
            for (int column = 0; column < result.data[row].length; column++) {
                result.data[row][column] = current.data[row][column] - previous.data[row][column];
            }
        }
        return result;
    }

    private NormalData copy(NormalData source) {
        NormalData result = new NormalData();
        result.res = source.res;
        result.bounds = source.bounds;
        result.unit = source.unit;
        result.data = new float[source.data.length][];
        for (int row = 0; row < source.data.length; row++) {
            result.data[row] = source.data[row].clone();
        }
        return result;
    }

    private DerivedSpec parse(String dataPath, CmaDataProperties.Derived definition) {
        if (dataPath == null || definition.getRef() == null || definition.getStep() <= 0) {
            throw new IllegalStateException("dataPath, ref and positive step are required for derived data " + dataPath);
        }
        String[] output = dataPath.split("/");
        if (output.length != 3) {
            throw new IllegalStateException("derived dataPath must be shortName/levelType/level: " + dataPath);
        }
        int levelType;
        double level;
        try {
            levelType = Integer.parseInt(output[1]);
            level = Double.parseDouble(output[2]);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("derived dataPath is invalid: " + dataPath, exception);
        }
        String aggr = definition.getAggr() == null || definition.getAggr().isBlank()
                ? "difference" : definition.getAggr().toLowerCase(Locale.ROOT);
        int count = "difference".equals(aggr) ? 1 : definition.getCount();
        if (!"difference".equals(aggr) && count < 2) {
            throw new IllegalStateException("aggregated derived data requires count >= 2: " + dataPath);
        }
        if (!List.of("difference", "avg", "max", "min").contains(aggr)) {
            throw new IllegalStateException("unsupported aggregation " + aggr + " for derived data " + dataPath);
        }
        String[] reference = definition.getRef().split("/");
        String sourceId = null;
        if (reference.length == 4 && sources.contains(reference[0])) {
            sourceId = CmaDataSourceRegistry.normalize(reference[0]);
            reference = Arrays.copyOfRange(reference, 1, reference.length);
        }
        if (reference.length != 3) {
            throw new IllegalStateException("derived ref must be shortName/levelType/level: " + definition.getRef());
        }
        try {
            int refLevelType = Integer.parseInt(reference[1]);
            double refLevel = Double.parseDouble(reference[2]);
            return new DerivedSpec(output[0], sourceId, reference[0], refLevelType, refLevel,
                    levelType, level, definition.getStep(), count, definition.getMinVT(), aggr);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("derived ref is invalid: " + definition.getRef(), exception);
        }
    }

    @FunctionalInterface
    public interface NormalDataLoader {
        NormalData load(String sourceId, String inittime, String forecast,
                        String shortName, int levelType, double level) throws IOException;
    }

    private static String key(String shortName, int levelType, double level) {
        String levelText = level == Math.rint(level) ? Long.toString((long) level) : Double.toString(level);
        return (shortName + "/" + levelType + "/" + levelText).toLowerCase(Locale.ROOT);
    }

    private record DerivedSpec(String name, String sourceId, String refName,
                               int refLevelType, double refLevel, int levelType, double level,
                               int step, int count, int minVT, String aggr) {
        private boolean isDifference() {
            return "difference".equals(aggr);
        }
    }
}
