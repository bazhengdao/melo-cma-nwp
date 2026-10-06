package com.meloxu.cmanwp;

import com.meloxu.cmanwp.config.CmaDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CmaDataProperties.class)
public class CmaNwpApplication {
    public static void main(String[] args) {
        SpringApplication.run(CmaNwpApplication.class, args);
    }
}
