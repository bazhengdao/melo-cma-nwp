package com.meloxu.cmanwp.service;

import com.meloxu.cmanwp.config.CmaDataProperties;
import com.meloxu.cmanwp.model.NormalData;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class CmaNwpUnitService {
    private final List<Rule> rules;

    public CmaNwpUnitService(CmaDataProperties properties) {
        this.rules = properties.getConversions().stream().map(Rule::new).toList();
    }

    public void apply(NormalData data, String shortName, int levelType, double level) {
        Rule rule = rules.stream()
                .filter(candidate -> candidate.matches(shortName, levelType, level))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return;
        }
        for (float[] row : data.data) {
            for (int column = 0; column < row.length; column++) {
                float value = row[column];
                if (Float.isFinite(value) && Math.abs(value) < 1.0e30f) {
                    row[column] = (float) (value * rule.scale + rule.offset);
                }
            }
        }
        data.unit = rule.to;
    }

    private static final class Rule {
        private final String[] path;
        private final double scale;
        private final double offset;
        private final String to;

        private Rule(CmaDataProperties.UnitConversion conversion) {
            if (conversion.getDataPath() == null || conversion.getDataPath().isBlank()
                    || !Double.isFinite(conversion.getScale())
                    || !Double.isFinite(conversion.getOffset())) {
                throw new IllegalStateException("unit conversion requires a valid dataPath, scale and offset");
            }
            this.path = normalize(conversion.getDataPath()).split("/", -1);
            if (path.length != 3) {
                throw new IllegalStateException("unit conversion dataPath must be shortName/levelType/level: "
                        + conversion.getDataPath());
            }
            this.scale = conversion.getScale();
            this.offset = conversion.getOffset();
            this.to = conversion.getTo();
        }

        private boolean matches(String shortName, int levelType, double level) {
            String[] value = {shortName, Integer.toString(levelType), number(level)};
            return Arrays.stream(new int[]{0, 1, 2})
                    .allMatch(index -> "*".equals(path[index]) || path[index].equalsIgnoreCase(value[index]));
        }
    }

    private static String normalize(String dataPath) {
        String[] parts = dataPath.trim().split("/", -1);
        if (parts.length >= 4) {
            parts = Arrays.copyOfRange(parts, parts.length - 3, parts.length);
        }
        return String.join("/", parts).toLowerCase(Locale.ROOT);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
