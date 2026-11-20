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
                    "rain": 1.0,
                    "showers": 0.2,
                    "snowfall": 0.0,
                    "weather_code": 61,
                    "cloud_cover": 85,
                    "wind_speed_10m": 18.5,
                    "wind_direction_10m": 220
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        WeatherSnapshot snapshot = provider.getCurrentWeather(51.5074, -0.1278).get();

        assertNotNull(snapshot);
        assertEquals(51.5074, snapshot.getLatitude(), 0.0001);
        assertEquals(-0.1278, snapshot.getLongitude(), 0.0001);
        assertTrue(snapshot.matchesLocation(51.5074, -0.1278));
        assertEquals(61, snapshot.getWeatherCode());
        assertEquals(12.5, snapshot.getTemperature());
        assertTrue(snapshot.getApparentTemperature().isPresent());
        assertEquals(10.2, snapshot.getApparentTemperature().getAsDouble());
        assertTrue(snapshot.getRelativeHumidity().isPresent());
        assertEquals(78.0, snapshot.getRelativeHumidity().getAsDouble());
        assertEquals(1.2, snapshot.getPrecipitation());
        assertEquals(1.0, snapshot.getRain());
        assertEquals(0.2, snapshot.getShowers());
        assertEquals(0.0, snapshot.getSnowfall());
        assertEquals(85.0, snapshot.getCloudCover());
        assertTrue(snapshot.getWindSpeed().isPresent());
        assertEquals(18.5, snapshot.getWindSpeed().getAsDouble());
        assertTrue(snapshot.getWindDirection().isPresent());
        assertEquals(220.0, snapshot.getWindDirection().getAsDouble());
        assertEquals("Europe/London", snapshot.getTimezone());
        assertNotNull(snapshot.getObservationTime());
        assertNotNull(snapshot.getFetchTime());
        assertEquals(1, requestCounter.get());
    }

    @Test
    @DisplayName("Verify forceRefresh bypasses fresh cache and sends new HTTP request")
    void testForceRefreshBypassesCache() throws Exception {
        responseCode = 200;
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T13:45",
                    "temperature_2m": 18.0,
                    "weather_code": 0
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));

        // 1. First fetch
        WeatherSnapshot snap1 = provider.getCurrentWeather(40.7128, -74.0060, false).get();
        assertEquals(18.0, snap1.getTemperature());
        assertEquals(1, requestCounter.get());

        // 2. Second fetch with forceRefresh=false uses cache (requestCounter stays 1)
        WeatherSnapshot snap2 = provider.getCurrentWeather(40.7128, -74.0060, false).get();
        assertEquals(18.0, snap2.getTemperature());
        assertEquals(1, requestCounter.get(), "Cached response should not send another request");

        // 3. Third fetch with forceRefresh=true bypasses cache and hits server
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T14:00",
                    "temperature_2m": 21.5,
                    "weather_code": 1
                  }
                }
                """;
        WeatherSnapshot snap3 = provider.getCurrentWeather(40.7128, -74.0060, true).get();
        assertEquals(21.5, snap3.getTemperature());
        assertEquals(2, requestCounter.get(), "forceRefresh=true must perform a real HTTP request");
    }

    @Test
    @DisplayName("Verify coordinate validation rejects NaN, infinite, and out-of-range values")
    void testCoordinateValidation() {
        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));

        assertThrows(ExecutionException.class, () -> provider.getCurrentWeather(Double.NaN, 0.0).get());
        assertThrows(ExecutionException.class, () -> provider.getCurrentWeather(0.0, Double.NaN).get());
        assertThrows(ExecutionException.class, () -> provider.getCurrentWeather(Double.POSITIVE_INFINITY, 0.0).get());
        assertThrows(ExecutionException.class, () -> provider.getCurrentWeather(0.0, Double.NEGATIVE_INFINITY).get());
        assertThrows(ExecutionException.class, () -> provider.getCurrentWeather(91.0, 0.0).get());
        assertThrows(ExecutionException.class, () -> provider.getCurrentWeather(0.0, -181.0).get());
    }

    @Test
    @DisplayName("Verify HTTP 429 response throws WeatherRateLimitException")
    void testRateLimitHandling() {
        responseCode = 429;
        retryAfterHeader = "120";
        responseBody = "Too Many Requests";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(48.8566, 2.3522).get());

        assertInstanceOf(WeatherRateLimitException.class, ex.getCause());
        WeatherRateLimitException rateLimitEx = (WeatherRateLimitException) ex.getCause();
        assertTrue(rateLimitEx.getRetryAfter().isPresent());
        assertEquals(Duration.ofSeconds(120), rateLimitEx.getRetryAfter().get());
    }

    @Test
    @DisplayName("Verify HTTP 500 error throws WeatherApiException")
    void testHttp500Handling() {
        responseCode = 500;
        responseBody = "Internal Server Error";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(48.8566, 2.3522).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
        WeatherApiException apiEx = (WeatherApiException) ex.getCause();
        assertEquals(500, apiEx.getStatusCode());
    }

    @Test
    @DisplayName("Verify malformed JSON throws WeatherApiException")
    void testMalformedJsonHandling() {
        responseCode = 200;
        responseBody = "{ invalid json !!! }";

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(48.8566, 2.3522).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Malformed JSON"));
    }

    @Test
    @DisplayName("Verify missing required fields throws WeatherApiException")
    void testMissingRequiredFields() {
        responseCode = 200;
        responseBody = """
                {
                  "timezone": "UTC",
                  "current": {
                    "time": "2026-11-20T13:45"
                  }
                }
                """;

        OpenMeteoProvider provider = createProvider(Duration.ofSeconds(5), Duration.ofMinutes(15));
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.getCurrentWeather(48.8566, 2.3522).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Missing required 'temperature_2m'"));
    }

    @Test
    @DisplayName("Verify concurrent requests for same coordinates are deduplicated")
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
