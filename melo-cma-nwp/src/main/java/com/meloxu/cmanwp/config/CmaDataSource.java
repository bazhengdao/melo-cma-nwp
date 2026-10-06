package com.meloxu.cmanwp.config;

import java.nio.file.Path;

public record CmaDataSource(String id, Path root, String filePrefix) {
    public Path file(String inittime, String forecast) {
        return root.resolve(inittime)
                .resolve(filePrefix + inittime + forecast + ".grb2")
                .normalize();
    }
}
