package com.minidoodle;

import com.minidoodle.shared.config.OutboxProperties;
import com.minidoodle.shared.config.SlotProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SlotProperties.class, OutboxProperties.class})
public class MiniDoodleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniDoodleApplication.class, args);
    }
}
