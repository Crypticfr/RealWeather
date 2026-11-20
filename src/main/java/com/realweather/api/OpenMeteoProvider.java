package com.realweather.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OpenMeteoProvider implements WeatherProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenMeteoProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final Duration cacheDuration;
    private final WeatherCache cache;
    private final ConcurrentHashMap<WeatherCache.CoordinateKey, CompletableFuture<WeatherSnapshot>> inflightRequests;

    public OpenMeteoProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                DEFAULT_BASE_URL,
                Duration.ofSeconds(10),
                Duration.ofMinutes(15));
    }

    public OpenMeteoProvider(HttpClient httpClient, String baseUrl, Duration requestTimeout, Duration cacheDuration) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout cannot be null");
        this.cacheDuration = Objects.requireNonNull(cacheDuration, "cacheDuration cannot be null");
        this.cache = new WeatherCache();
        this.inflightRequests = new ConcurrentHashMap<>();
    }

    @Override
    public String getId() {
        return "open-meteo";
    }

    @Override
    public String getName() {
        return "Open-Meteo";
    }

    @Override
    public CompletableFuture<WeatherSnapshot> getCurrentWeather(double latitude, double longitude) {
        return getCurrentWeather(latitude, longitude, false);
    }

    @Override
    public CompletableFuture<WeatherSnapshot> getCurrentWeather(double latitude, double longitude, boolean forceRefresh) {
        try {
            WeatherProvider.validateCoordinates(latitude, longitude);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        if (!forceRefresh) {
            Optional<WeatherSnapshot> fresh = cache.getIfFresh(latitude, longitude, cacheDuration);
            if (fresh.isPresent()) {
                return CompletableFuture.completedFuture(fresh.get());
            }
        }

        WeatherCache.CoordinateKey key = WeatherCache.CoordinateKey.of(latitude, longitude);
        if (forceRefresh) {
            CompletableFuture<WeatherSnapshot> future = executeRequest(latitude, longitude);
            inflightRequests.put(key, future);
            future.whenComplete((snapshot, err) -> {
                inflightRequests.remove(key, future);
                if (snapshot != null) {
                    cache.put(latitude, longitude, snapshot);
                }
            });
            return future;
        }

        return inflightRequests.computeIfAbsent(key, k -> {
            CompletableFuture<WeatherSnapshot> future = executeRequest(latitude, longitude);
            future.whenComplete((snapshot, err) -> {
                inflightRequests.remove(k, future);
                if (snapshot != null) {
                    cache.put(latitude, longitude, snapshot);
                }
            });
            return future;
        });
    }

    @Override
    public Optional<WeatherSnapshot> getCachedWeather(double latitude, double longitude) {
        return cache.getAny(latitude, longitude);
    }

    public WeatherCache getCache() {
        return cache;
    }

    private CompletableFuture<WeatherSnapshot> executeRequest(double latitude, double longitude) {
        String uriStr = String.format(Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,rain,showers,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m&timezone=auto",
                baseUrl, latitude, longitude);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode == 429) {
                        Duration retryAfter = parseRetryAfter(response);
                        LOGGER.warn("Open-Meteo rate limit reached. Retry after: {}", retryAfter);
                        return CompletableFuture.failedFuture(new WeatherRateLimitException("Open-Meteo rate limit exceeded (HTTP 429)", retryAfter));
                    }

                    if (statusCode != 200) {
                        LOGGER.warn("Open-Meteo returned HTTP error: {}", statusCode);
                        return CompletableFuture.failedFuture(new WeatherApiException("Open-Meteo API returned error status: " + statusCode, statusCode));
                    }

                    try {
                        WeatherSnapshot snapshot = parseWeatherResponse(response.body(), latitude, longitude, Instant.now());
                        return CompletableFuture.completedFuture(snapshot);
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse Open-Meteo response: {}", e.getMessage());
                        return CompletableFuture.failedFuture(new WeatherApiException("Failed to parse weather JSON: " + e.getMessage(), e));
                    }
                });
    }

    private WeatherSnapshot parseWeatherResponse(String body, double latitude, double longitude, Instant fetchTime) throws WeatherApiException {
        if (body == null || body.isBlank()) {
            throw new WeatherApiException("Empty response body from Open-Meteo");
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            throw new WeatherApiException("Malformed JSON received from weather API", e);
        }

        if (!root.has("current") || root.get("current").isJsonNull()) {
            throw new WeatherApiException("Missing 'current' object in Open-Meteo response");
        }

        JsonObject current = root.getAsJsonObject("current");

        if (!current.has("temperature_2m") || current.get("temperature_2m").isJsonNull()) {
            throw new WeatherApiException("Missing required 'temperature_2m' in weather data");
        }
        if (!current.has("weather_code") || current.get("weather_code").isJsonNull()) {
            throw new WeatherApiException("Missing required 'weather_code' in weather data");
        }

        double temperature = current.get("temperature_2m").getAsDouble();
        int weatherCode = current.get("weather_code").getAsInt();

        Double apparentTemperature = current.has("apparent_temperature") && !current.get("apparent_temperature").isJsonNull()
                ? current.get("apparent_temperature").getAsDouble() : null;

        Double relativeHumidity = current.has("relative_humidity_2m") && !current.get("relative_humidity_2m").isJsonNull()
                ? current.get("relative_humidity_2m").getAsDouble() : null;

        double precipitation = current.has("precipitation") && !current.get("precipitation").isJsonNull()
                ? current.get("precipitation").getAsDouble() : 0.0;

        double rain = current.has("rain") && !current.get("rain").isJsonNull()
                ? current.get("rain").getAsDouble() : 0.0;

        double showers = current.has("showers") && !current.get("showers").isJsonNull()
                ? current.get("showers").getAsDouble() : 0.0;

        double snowfall = current.has("snowfall") && !current.get("snowfall").isJsonNull()
                ? current.get("snowfall").getAsDouble() : 0.0;

        double cloudCover = current.has("cloud_cover") && !current.get("cloud_cover").isJsonNull()
                ? current.get("cloud_cover").getAsDouble() : 0.0;

        Double windSpeed = current.has("wind_speed_10m") && !current.get("wind_speed_10m").isJsonNull()
                ? current.get("wind_speed_10m").getAsDouble() : null;

        Double windDirection = current.has("wind_direction_10m") && !current.get("wind_direction_10m").isJsonNull()
                ? current.get("wind_direction_10m").getAsDouble() : null;

        Instant observationTime = fetchTime;
        if (current.has("time") && !current.get("time").isJsonNull()) {
            String timeStr = current.get("time").getAsString();
            try {
                if (timeStr.endsWith("Z")) {
                    observationTime = Instant.parse(timeStr);
                } else {
                    LocalDateTime ldt = LocalDateTime.parse(timeStr);
                    int offsetSeconds = root.has("utc_offset_seconds") && !root.get("utc_offset_seconds").isJsonNull()
                            ? root.get("utc_offset_seconds").getAsInt() : 0;
                    observationTime = ldt.toInstant(ZoneOffset.ofTotalSeconds(offsetSeconds));
                }
            } catch (Exception ignored) {
            }
        }

        String timezone = root.has("timezone") && !root.get("timezone").isJsonNull()
                ? root.get("timezone").getAsString() : "UTC";

        return new WeatherSnapshot(
                latitude,
                longitude,
                weatherCode,
                temperature,
                apparentTemperature,
                relativeHumidity,
                precipitation,
                rain,
                showers,
                snowfall,
                cloudCover,
                windSpeed,
                windDirection,
                observationTime,
                timezone,
                fetchTime
        );
    }

    private Duration parseRetryAfter(HttpResponse<?> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isPresent()) {
            try {
                long seconds = Long.parseLong(header.get().trim());
                return Duration.ofSeconds(seconds);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
