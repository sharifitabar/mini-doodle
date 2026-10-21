package com.minidoodle.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * YAML binding for {@code app.outbox.*}. The relay reads the poll interval, the batch size, the
 * attempt limit, and the cleanup settings. Every value has a default in {@code application.yml}.
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        @NotNull Duration pollInterval,
        @Min(1) int batchSize,
        @Min(1) int maxAttempts,
        @NotNull Duration cleanupInterval,
        @NotNull Duration retention) {}
