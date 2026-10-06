package com.meloxu.cmanwp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CmaContourConfigurationTest {
    @Autowired
    private CmaDataProperties properties;

    @Test
    void loadsContourPathsUnderCmaContours() {
        assertTrue(properties.getContours().size() >= 50);
        assertTrue(properties.getContours().stream()
                .anyMatch(item -> "tmp/103/2".equals(item.getDataPath())));
        assertTrue(properties.getContours().stream()
                .anyMatch(item -> "hgt/100/500".equals(item.getDataPath())));
    }
}
