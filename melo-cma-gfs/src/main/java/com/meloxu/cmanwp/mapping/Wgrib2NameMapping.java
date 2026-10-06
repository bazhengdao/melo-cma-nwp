package com.meloxu.cmanwp.mapping;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Component
public class Wgrib2NameMapping {
    private static final String RESOURCE = "wgrib2-names.properties";

    private final Map<String, String> values;

    public Wgrib2NameMapping() {
        Properties properties = new Properties();
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load " + RESOURCE, exception);
        }
        values = properties.stringPropertyNames().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, properties::getProperty));
    }

    public String resolve(String key) {
        return values.getOrDefault(key, key);
    }
}
