package com.realweather.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherConfigTest {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    @DisplayName("Verify default configuration values")
    void testDefaultValues() {
        WeatherConfig config = new WeatherConfig();
        config.validate();

        assertTrue(config.isEnabled());
        assertEquals("open-meteo", config.getWeatherProvider());
        assertEquals("London, United Kingdom", config.getLocationName());
        assertEquals(51.5074, config.getLatitude(), 0.0001);
        assertEquals(-0.1278, config.getLongitude(), 0.0001);
        assertEquals(15, config.getUpdateIntervalMinutes());
        assertTrue(config.isSyncRain());
        assertTrue(config.isSyncThunder());
        assertTrue(config.isSyncSnow());
        assertEquals(10, config.getApiTimeoutSeconds());
        assertEquals(60, config.getRetryIntervalSeconds());
        assertEquals(30, config.getCacheDurationMinutes());
        assertFalse(config.isDebugLogging());
    }

    @Test
    @DisplayName("Verify coordinate clamping for out-of-range values")
    void testCoordinateClamping() {
        WeatherConfig config = new WeatherConfig();
        config.setLatitude(120.0);
        config.setLongitude(-200.0);
        config.validate();

        assertEquals(90.0, config.getLatitude(), 0.0001);
        assertEquals(-180.0, config.getLongitude(), 0.0001);

        config.setLatitude(-150.0);
        config.setLongitude(300.0);
        config.validate();

        assertEquals(-90.0, config.getLatitude(), 0.0001);
        assertEquals(180.0, config.getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Verify NaN and Infinite coordinates are sanitized to valid values")
    void testSanitizesNaNAndInfiniteCoordinates() {
        WeatherConfig config = new WeatherConfig();
        config.setLatitude(Double.NaN);
        config.setLongitude(Double.POSITIVE_INFINITY);
        config.validate();

        assertFalse(Double.isNaN(config.getLatitude()));
        assertFalse(Double.isInfinite(config.getLongitude()));
        assertEquals(0.0, config.getLatitude(), 0.0001);
        assertEquals(0.0, config.getLongitude(), 0.0001);

        config.setLatitude(Double.NEGATIVE_INFINITY);
        config.setLongitude(Double.NaN);
        config.validate();

        assertEquals(0.0, config.getLatitude(), 0.0001);
        assertEquals(0.0, config.getLongitude(), 0.0001);
    }

    @Test
    @DisplayName("Verify update interval and timeout constraints")
    void testIntervalValidation() {
        WeatherConfig config = new WeatherConfig();
        config.setUpdateIntervalMinutes(-5);
        config.setApiTimeoutSeconds(0);
        config.setRetryIntervalSeconds(2);
        config.setCacheDurationMinutes(-10);
        config.validate();

        assertEquals(15, config.getUpdateIntervalMinutes());
        assertEquals(10, config.getApiTimeoutSeconds());
        assertEquals(60, config.getRetryIntervalSeconds());
        assertEquals(30, config.getCacheDurationMinutes());
    }

    @Test
    @DisplayName("Verify JSON serialization and deserialization roundtrip")
    void testJsonRoundtrip() {
        WeatherConfig original = new WeatherConfig();
        original.setLocationName("Tokyo, Japan");
        original.setLatitude(35.6764);
        original.setLongitude(139.6500);
        original.setUpdateIntervalMinutes(30);
        original.setDebugLogging(true);

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("Tokyo, Japan"));

        WeatherConfig deserialized = gson.fromJson(json, WeatherConfig.class);
        assertNotNull(deserialized);
        deserialized.validate();

        assertEquals("Tokyo, Japan", deserialized.getLocationName());
        assertEquals(35.6764, deserialized.getLatitude(), 0.0001);
        assertEquals(139.6500, deserialized.getLongitude(), 0.0001);
        assertEquals(30, deserialized.getUpdateIntervalMinutes());
        assertTrue(deserialized.isDebugLogging());
    }
}
