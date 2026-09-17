package newsFeed.model.fetcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public abstract class ADPFetcher extends Fetcher {

    protected String fetchData(String url) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "NewsFeed/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new ADPRequestException("ADP returned HTTP " + response.statusCode(),
                        retryAfter(response.headers().firstValue("Retry-After").orElse("")));
            }

            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ADP request interrupted.", e);
        } catch (IOException e) {
            throw new IllegalStateException("ADP request failed: " + e.getMessage(), e);
        }
    }

    private static Duration retryAfter(String value) {
        try {
            Duration duration = value.matches("[0-9]+") ? Duration.ofSeconds(Long.parseLong(value))
                    : Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return duration.isNegative() ? Duration.ZERO : duration;
        } catch (RuntimeException e) {
            return Duration.ZERO;
        }
    }

    protected static final class ADPRequestException extends IllegalStateException {
        private final Duration retryAfter;

        ADPRequestException(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }
}