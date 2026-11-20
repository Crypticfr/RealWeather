package com.realweather.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenMeteoProviderTest {
    private static HttpServer server;
    private static int serverPort;
    private static volatile String responseBody = "";
    private static volatile int responseCode = 200;
    private static volatile String retryAfterHeader = null;
    private static volatile long responseDelayMs = 0;
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    @BeforeAll
    static void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/forecast", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCounter.incrementAndGet();

                if (responseDelayMs > 0) {
                    try {
                        Thread.sleep(responseDelayMs);
                    } catch (InterruptedException ignored) {
                    }
                }

                if (retryAfterHeader != null) {
                    exchange.getResponseHeaders().set("Retry-After", retryAfterHeader);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");

                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseCode, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();
        serverPort = server.getAddress().getPort();
    }

    @AfterAll
    static void tearDownServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void resetServerState() {
        responseBody = "";
        responseCode = 200;
        retryAfterHeader = null;
        responseDelayMs = 0;
        requestCounter.set(0);
    }

    private OpenMeteoProvider createProvider(Duration requestTimeout, Duration cacheDuration) {
        String baseUrl = "http://127.0.0.1:" + serverPort + "/v1/forecast";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return new OpenMeteoProvider(client, baseUrl, requestTimeout, cacheDuration);
    }

    @Test
    @DisplayName("Verify successful weather response parsing with full fields")
    void testSuccessfulWeatherResponse() throws Exception {
        responseCode = 200;
        responseBody = """
                {
                  "latitude": 51.5,
                  "longitude": -0.12,
                  "utc_offset_seconds": 0,
                  "timezone": "Europe/London",
                  "current": {
                    "time": "2026-11-20T13:45",
                    "temperature_2m": 12.5,
                    "apparent_temperature": 10.2,
                    "relative_humidity_2m": 78,
                    "precipitation": 1.2,
                    "rain": 1.2,
                    "showers": 0.0,
                    "snowfall": 0.0,
                    "weather_code": 61,
                    "cloud_cover": 85,
                    "wind_speed_10m": 14.2,
                    "wind_direction_10m": 210
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        WeatherSnapshot snapshot = provider.getCurrentWeather(51.5074, -0.1278).get();

        assertNotNull(snapshot);
        assertEquals(61, snapshot.getWeatherCode());
        assertEquals(12.5, snapshot.getTemperature(), 0.001);
        assertTrue(snapshot.getApparentTemperature().isPresent());
        assertEquals(10.2, snapshot.getApparentTemperature().getAsDouble(), 0.001);
        assertTrue(snapshot.getRelativeHumidity().isPresent());
        assertEquals(78.0, snapshot.getRelativeHumidity().getAsDouble(), 0.001);
        assertEquals(1.2, snapshot.getPrecipitation(), 0.001);
        assertEquals(1.2, snapshot.getRain(), 0.001);
        assertEquals(0.0, snapshot.getShowers(), 0.001);
        assertEquals(0.0, snapshot.getSnowfall(), 0.001);
        assertEquals(85.0, snapshot.getCloudCover(), 0.001);
        assertTrue(snapshot.getWindSpeed().isPresent());
        assertEquals(14.2, snapshot.getWindSpeed().getAsDouble(), 0.001);
        assertTrue(snapshot.getWindDirection().isPresent());
        assertEquals(210.0, snapshot.getWindDirection().getAsDouble(), 0.001);
        assertEquals("Europe/London", snapshot.getTimezone());
        assertEquals(1, requestCounter.get());
    }

    @Test
    @DisplayName("Verify coordinate validation rejects out-of-range coordinates")
    void testInvalidCoordinates() {
        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));

        ExecutionException ex1 = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(95.0, 0.0).get());
        assertInstanceOf(IllegalArgumentException.class, ex1.getCause());

        ExecutionException ex2 = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(0.0, 190.0).get());
        assertInstanceOf(IllegalArgumentException.class, ex2.getCause());

        assertEquals(0, requestCounter.get(), "Invalid coordinates should not make HTTP calls");
    }

    @Test
    @DisplayName("Verify HTTP 400 Bad Request error propagation")
    void testHttp400Error() {
        responseCode = 400;
        responseBody = "{\"error\":true,\"reason\":\"Invalid parameter\"}";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(51.5, -0.1).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
        WeatherApiException apiEx = (WeatherApiException) ex.getCause();
        assertEquals(400, apiEx.getStatusCode());
    }

    @Test
    @DisplayName("Verify HTTP 429 Rate Limit error with Retry-After header")
    void testHttp429WithRetryAfter() {
        responseCode = 429;
        retryAfterHeader = "120";
        responseBody = "{\"error\":true,\"reason\":\"Rate limit exceeded\"}";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(51.5, -0.1).get());

        assertInstanceOf(WeatherRateLimitException.class, ex.getCause());
        WeatherRateLimitException rateLimitEx = (WeatherRateLimitException) ex.getCause();
        assertEquals(429, rateLimitEx.getStatusCode());
        assertTrue(rateLimitEx.getRetryAfter().isPresent());
        assertEquals(Duration.ofSeconds(120), rateLimitEx.getRetryAfter().get());
    }

    @Test
    @DisplayName("Verify HTTP 500 Internal Server Error handling")
    void testHttp500Error() {
        responseCode = 500;
        responseBody = "Internal Server Error";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(51.5, -0.1).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
        WeatherApiException apiEx = (WeatherApiException) ex.getCause();
        assertTrue(apiEx.isServerError());
        assertEquals(500, apiEx.getStatusCode());
    }

    @Test
    @DisplayName("Verify request timeout handling")
    void testNetworkTimeout() {
        responseCode = 200;
        responseDelayMs = 500; // 500ms delay
        responseBody = "{}";

        OpenMeteoProvider provider = createProvider(Duration.ofMillis(100), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(51.5, -0.1).get());

        assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("Verify malformed JSON handling")
    void testMalformedJson() {
        responseCode = 200;
        responseBody = "{not-valid-json";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(51.5, -0.1).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
    }

    @Test
    @DisplayName("Verify missing required weather fields handling")
    void testMissingRequiredFields() {
        responseCode = 200;
        // Missing temperature_2m and weather_code
        responseBody = """
                {
                  "current": {
                    "time": "2026-11-20T13:45"
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(51.5, -0.1).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
    }

    @Test
    @DisplayName("Verify cache hit prevents second HTTP request")
    void testCacheHit() throws Exception {
        responseCode = 200;
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T13:45",
                    "temperature_2m": 15.0,
                    "weather_code": 0
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));

        WeatherSnapshot first = provider.getCurrentWeather(40.7128, -74.0060).get();
        assertEquals(1, requestCounter.get());

        WeatherSnapshot second = provider.getCurrentWeather(40.7128, -74.0060).get();
        assertEquals(1, requestCounter.get(), "Second call within cache window should not hit HTTP server");
        assertEquals(first.getTemperature(), second.getTemperature());
    }

    @Test
    @DisplayName("Verify cache expiry causes new HTTP request")
    void testCacheExpiry() throws Exception {
        responseCode = 200;
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T13:45",
                    "temperature_2m": 15.0,
                    "weather_code": 0
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMillis(20));

        provider.getCurrentWeather(48.8566, 2.3522).get();
        assertEquals(1, requestCounter.get());

        Thread.sleep(30); // wait past cache expiry (20ms)

        provider.getCurrentWeather(48.8566, 2.3522).get();
        assertEquals(2, requestCounter.get(), "Expired cache should trigger new HTTP request");
    }

    @Test
    @DisplayName("Verify concurrent requests for identical coordinates are deduplicated")
    void testConcurrentRequestDeduplication() throws Exception {
        responseCode = 200;
        responseDelayMs = 50; // allow concurrency to overlap
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T13:45",
                    "temperature_2m": 22.0,
                    "weather_code": 1
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));

        int threadCount = 8;
        List<CompletableFuture<WeatherSnapshot>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(provider.getCurrentWeather(35.6762, 139.6503));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        for (CompletableFuture<WeatherSnapshot> future : futures) {
            assertEquals(22.0, future.get().getTemperature());
        }

        assertEquals(1, requestCounter.get(), "All concurrent requests should share a single HTTP flight");
    }

    @Test
    @DisplayName("Verify cached weather remains available during API outage")
    void testCachedDataPreservedDuringOutage() throws Exception {
        // Step 1: Successful request
        responseCode = 200;
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T13:45",
                    "temperature_2m": 25.0,
                    "weather_code": 0
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMillis(10));
        provider.getCurrentWeather(10.0, 20.0).get();

        Thread.sleep(20); // expire fresh cache

        // Step 2: Next request fails with HTTP 500
        responseCode = 500;
        responseBody = "API Down";

        assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(10.0, 20.0).get());

        // Step 3: Stale cache is still inspectable via getCachedWeather
        Optional<WeatherSnapshot> cached = provider.getCachedWeather(10.0, 20.0);
        assertTrue(cached.isPresent());
        assertEquals(25.0, cached.get().getTemperature());
    }
}
