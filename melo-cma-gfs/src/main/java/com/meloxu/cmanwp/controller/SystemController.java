package com.meloxu.cmanwp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("name", "cma-gfs", "status", "ok");
    }
}
