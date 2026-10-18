package com.minidoodle;

import com.minidoodle.shared.config.SlotProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SlotProperties.class)
public class MiniDoodleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniDoodleApplication.class, args);
    }
}
