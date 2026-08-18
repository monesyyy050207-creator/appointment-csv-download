package dev.infrai.appointments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("infrai")
public record StorageProperties(
        String baseUrl,
        String apiKey,
        String bucket,
        int downloadExpiresSeconds) {
}
