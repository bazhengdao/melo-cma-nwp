package com.meloxu.cmanwp.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class CmaDataSourceRegistry {
    private final Map<String, CmaDataSource> sources;

    public CmaDataSourceRegistry(CmaDataProperties properties) {
        this.sources = new LinkedHashMap<>();
        properties.getSources().forEach((id, source) -> {
            if (source.getRoot() == null || source.getRoot().isBlank()) {
                throw new IllegalStateException("root is required for data source " + id);
            }
            String normalizedId = normalize(id);
            sources.put(normalizedId, new CmaDataSource(normalizedId,
                    Path.of(source.getRoot()).toAbsolutePath().normalize(), source.getFilePrefix()));
        });
        if (sources.isEmpty()) {
            throw new IllegalStateException("at least one CMA data source is required");
        }
    }

    public CmaDataSource get(String id) {
        CmaDataSource source = sources.get(normalize(id));
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown data source: " + id);
        }
        return source;
    }

    public boolean contains(String id) {
        return sources.containsKey(normalize(id));
    }

    public static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
