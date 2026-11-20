package com.realweather.location;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.realweather.api.WeatherApiException;
import com.realweather.api.WeatherRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class OpenMeteoGeocodingProvider implements GeocodingProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenMeteoGeocodingProvider.class);
    private static final String DEFAULT_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

    public OpenMeteoGeocodingProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                DEFAULT_BASE_URL,
                Duration.ofSeconds(10));
    }

    public OpenMeteoGeocodingProvider(HttpClient httpClient, String baseUrl, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout cannot be null");
    }

    @Override
    public CompletableFuture<List<LocationData>> search(String query, int maxResults) {
        if (query == null || query.trim().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Search query cannot be blank"));
        }

        int count = Math.max(1, Math.min(50, maxResults));
        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String uriStr = baseUrl + "?name=" + encodedQuery + "&count=" + count + "&language=en&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode == 429) {
                        LOGGER.warn("Geocoding rate limit exceeded (HTTP 429)");
                        return CompletableFuture.failedFuture(new WeatherRateLimitException("Geocoding rate limit exceeded", null));
                    }
                    if (statusCode != 200) {
                        LOGGER.warn("Geocoding API returned HTTP error: {}", statusCode);
                        return CompletableFuture.failedFuture(new WeatherApiException("Geocoding API error: HTTP " + statusCode, statusCode));
                    }

                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (!root.has("results") || root.get("results").isJsonNull()) {
                            return CompletableFuture.completedFuture(List.of());
                        }

                        JsonArray resultsArray = root.getAsJsonArray("results");
                        List<LocationData> locations = new ArrayList<>(resultsArray.size());
                        for (JsonElement elem : resultsArray) {
                            if (!elem.isJsonObject()) continue;
                            JsonObject obj = elem.getAsJsonObject();

                            String name = obj.has("name") && !obj.get("name").isJsonNull()
                                    ? obj.get("name").getAsString() : "Unknown";
                            String country = obj.has("country") && !obj.get("country").isJsonNull()
                                    ? obj.get("country").getAsString() : null;
                            String countryCode = obj.has("country_code") && !obj.get("country_code").isJsonNull()
                                    ? obj.get("country_code").getAsString() : null;
                            String admin = obj.has("admin1") && !obj.get("admin1").isJsonNull()
                                    ? obj.get("admin1").getAsString() : null;
                            double lat = obj.has("latitude") ? obj.get("latitude").getAsDouble() : 0.0;
                            double lon = obj.has("longitude") ? obj.get("longitude").getAsDouble() : 0.0;
                            String tz = obj.has("timezone") && !obj.get("timezone").isJsonNull()
                                    ? obj.get("timezone").getAsString() : "UTC";

                            locations.add(new LocationData(name, country, countryCode, admin, lat, lon, tz));
                        }
                        return CompletableFuture.completedFuture(List.copyOf(locations));
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse geocoding response: {}", e.getMessage());
                        return CompletableFuture.failedFuture(new WeatherApiException("Failed to parse geocoding response: " + e.getMessage(), e));
                    }
                });
    }
}
