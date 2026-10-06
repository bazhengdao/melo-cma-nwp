package com.meloxu.cmanwp.mapping;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "cma.wgrib2")
public class Wgrib2NameMapping {
    private Map<String, String> names = Map.of();

    public String resolve(String key) {
        return names.getOrDefault(key, key);
    }

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names == null ? Map.of() : Map.copyOf(names);
    }
}
