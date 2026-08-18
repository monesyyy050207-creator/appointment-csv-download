package dev.infrai.appointments.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.infrai.appointments.config.StorageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InfraiStorageClient {
    // Canonical calls: infrai.storage.bucket.create and infrai.storage.object.presign.
    private static final int MAX_ATTEMPTS = 4;
    private final StorageProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    public InfraiStorageClient(StorageProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void createBucket(String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", properties.bucket());
        body.put("idempotency_key", idempotencyKey);
        call("POST", "/v1/storage/bucket/create", body);
    }

    public SignedUrl presignPut(String key, String contentType, long maxBytes, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "put");
        body.put("expires_seconds", 300);
        body.put("content_type", contentType);
        body.put("max_bytes", maxBytes);
        body.put("idempotency_key", idempotencyKey);
        return signedUrl(call("POST", presignPath(key), body));
    }

    public SignedUrl presignGet(String key, String disposition) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "get");
        body.put("expires_seconds", properties.downloadExpiresSeconds());
        body.put("response_disposition", disposition);
        return signedUrl(call("POST", presignPath(key), body));
    }

    public void upload(SignedUrl signedUrl, byte[] csv) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl.url()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/csv; charset=utf-8")
                .method("PUT", HttpRequest.BodyPublishers.ofByteArray(csv))
                .build();
        sendWithRetry(request, false);
    }

    public int downloadExpiresSeconds() {
        return properties.downloadExpiresSeconds();
    }

    private String presignPath(String key) {
        return "/v1/storage/object/presign/" + segment(properties.bucket()) + "/" + segment(key);
    }

    private JsonNode call(String method, String path, Map<String, ?> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = sendWithRetry(request, true);
            JsonNode envelope = json.readTree(response.body());
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                throw new InfraiException(
                        error.path("code").asText("REQUEST_REJECTED"),
                        error.path("message").asText("Storage request was rejected"),
                        response.statusCode());
            }
            return envelope.path("data");
        } catch (IOException e) {
            throw new InfraiException("INVALID_RESPONSE", "Could not decode storage response", 502, e);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, boolean envelopeResponse) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                    sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                if (!envelopeResponse && response.statusCode() >= 400) {
                    throw new InfraiException("UPLOAD_REJECTED", "CSV upload was rejected", response.statusCode());
                }
                return response;
            } catch (IOException e) {
                if (attempt + 1 == MAX_ATTEMPTS) {
                    throw new InfraiException("TRANSPORT_ERROR", "Storage request could not be completed", 502, e);
                }
                sleep(250L << attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("REQUEST_INTERRUPTED", "Storage request was interrupted", 503, e);
            }
        }
        throw new IllegalStateException("Retry loop ended unexpectedly");
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> parseRetryAfter(value, attempt))
                .orElse(250L << attempt);
    }

    private long parseRetryAfter(String value, int attempt) {
        try {
            return Math.max(1_000L, Long.parseLong(value) * 1_000L);
        } catch (NumberFormatException ignored) {
            return 250L << attempt;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("REQUEST_INTERRUPTED", "Storage request was interrupted", 503, e);
        }
    }

    private SignedUrl signedUrl(JsonNode data) {
        return new SignedUrl(data.path("url").asText());
    }

    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record SignedUrl(String url) {
    }

    public static class InfraiException extends RuntimeException {
        private final String code;
        private final int upstreamStatus;

        public InfraiException(String code, String message, int upstreamStatus) {
            super(message);
            this.code = code;
            this.upstreamStatus = upstreamStatus;
        }

        public InfraiException(String code, String message, int upstreamStatus, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.upstreamStatus = upstreamStatus;
        }

        public String code() {
            return code;
        }

        public HttpStatus callerStatus() {
            if (upstreamStatus >= 400 && upstreamStatus < 500) {
                return HttpStatus.valueOf(upstreamStatus);
            }
            return HttpStatus.BAD_GATEWAY;
        }
    }
}
