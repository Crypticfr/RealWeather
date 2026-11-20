package com.realweather.location;

import com.realweather.api.WeatherApiException;
import com.realweather.api.WeatherRateLimitException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenMeteoGeocodingProviderTest {
    private static HttpServer server;
    private static int serverPort;
    private static volatile String responseBody = "";
    private static volatile int responseCode = 200;
    private static final AtomicInteger requestCounter = new AtomicInteger(0);

    @BeforeAll
    static void setUpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/search", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requestCounter.incrementAndGet();
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
    void resetState() {
        responseBody = "";
        responseCode = 200;
        requestCounter.set(0);
    }

    private OpenMeteoGeocodingProvider createProvider() {
        String baseUrl = "http://127.0.0.1:" + serverPort + "/v1/search";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return new OpenMeteoGeocodingProvider(client, baseUrl, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Verify successful geocoding search returning multiple results")
    void testSuccessfulGeocodingSearch() throws Exception {
        responseCode = 200;
        responseBody = """
                {
                  "results": [
                    {
                      "id": 2950159,
                      "name": "Berlin",
                      "latitude": 52.52437,
                      "longitude": 13.41053,
                      "country_code": "DE",
                      "timezone": "Europe/Berlin",
                      "country": "Germany",
                      "admin1": "Land Berlin"
                    },
                    {
                      "id": 5108620,
                      "name": "Berlin",
                      "latitude": 39.79123,
                      "longitude": -74.92905,
                      "country_code": "US",
                      "timezone": "America/New_York",
                      "country": "United States",
                      "admin1": "New Jersey"
                    }
                  ]
                }
                """;

        OpenMeteoGeocodingProvider provider = createProvider();
        List<LocationData> results = provider.search("Berlin", 5).get();

        assertNotNull(results);
        assertEquals(2, results.size());

        LocationData first = results.get(0);
        assertEquals("Berlin", first.name());
        assertEquals("Germany", first.country());
        assertEquals("DE", first.countryCode());
        assertEquals("Land Berlin", first.administrativeRegion());
        assertEquals(52.52437, first.latitude(), 0.0001);
        assertEquals(13.41053, first.longitude(), 0.0001);
        assertEquals("Europe/Berlin", first.timezone());
        assertEquals("Berlin, Land Berlin, Germany", first.getDisplayName());

        LocationData second = results.get(1);
        assertEquals("Berlin", second.name());
        assertEquals("United States", second.country());
        assertEquals("US", second.countryCode());
        assertEquals("New Jersey", second.administrativeRegion());
    }

    @Test
    @DisplayName("Verify empty geocoding results returns empty list")
    void testEmptyGeocodingResults() throws Exception {
        responseCode = 200;
        responseBody = "{\"generationtime_ms\":0.12}"; // Open-Meteo returns no results key when not found

        OpenMeteoGeocodingProvider provider = createProvider();
        List<LocationData> results = provider.search("NonExistentPlaceXYZ", 5).get();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Verify blank or null search query rejects immediately without network call")
    void testBlankQueryRejection() {
        OpenMeteoGeocodingProvider provider = createProvider();

        ExecutionException ex1 = assertThrows(ExecutionException.class, () ->
                provider.search(null, 5).get());
        assertInstanceOf(IllegalArgumentException.class, ex1.getCause());

        ExecutionException ex2 = assertThrows(ExecutionException.class, () ->
                provider.search("   ", 5).get());
        assertInstanceOf(IllegalArgumentException.class, ex2.getCause());

        assertEquals(0, requestCounter.get());
    }

    @Test
    @DisplayName("Verify geocoding HTTP 429 rate limit error")
    void testGeocodingRateLimit() {
        responseCode = 429;
        responseBody = "Rate limited";

        OpenMeteoGeocodingProvider provider = createProvider();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.search("London", 5).get());

        assertInstanceOf(WeatherRateLimitException.class, ex.getCause());
    }

    @Test
    @DisplayName("Verify geocoding HTTP 500 error")
    void testGeocodingServerError() {
        responseCode = 500;
        responseBody = "Server error";

        OpenMeteoGeocodingProvider provider = createProvider();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.search("Paris", 5).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
        WeatherApiException apiEx = (WeatherApiException) ex.getCause();
        assertEquals(500, apiEx.getStatusCode());
    }

    @Test
    @DisplayName("Verify geocoding malformed JSON error")
    void testGeocodingMalformedJson() {
        responseCode = 200;
        responseBody = "{bad-json";

        OpenMeteoGeocodingProvider provider = createProvider();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                provider.search("Tokyo", 5).get());

        assertInstanceOf(WeatherApiException.class, ex.getCause());
    }
}
