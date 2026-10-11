package com.meloxu.cmanwp.mapping;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class Wgrib2NameMapping {
    private static final String RESOURCE = "wgrib2-names.yml";
    private final Map<String, String> names;

    @SuppressWarnings("unchecked")
    public Wgrib2NameMapping() {
        Resource resource = new FileSystemResource("/app/config/" + RESOURCE);
        if (!resource.exists()) {
            resource = new ClassPathResource(RESOURCE);
        }
        try (InputStream input = resource.getInputStream()) {
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> cma = map(root.get("cma"));
            Map<String, Object> wgrib2 = map(cma.get("wgrib2"));
            Map<String, Object> configured = map(wgrib2.get("names"));
            Map<String, String> loaded = new LinkedHashMap<>();
            configured.forEach((key, value) -> loaded.put(key, String.valueOf(value)));
            names = Map.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load " + RESOURCE, exception);
        }
    }

    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }

    public String resolve(String key) {
        return names.getOrDefault(key, key);
    }

}
