package com.realweather.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WeatherCacheTest {

    @Test
    @DisplayName("Verify put, getIfFresh, and getAny on cache")
    void testCacheFreshAndStaleRetrieval() throws Exception {
        WeatherCache cache = new WeatherCache();
        Instant now = Instant.now();

        WeatherSnapshot snapshot = new WeatherSnapshot(
                0, 20.0, 20.0, 50.0, 0.0, 0.0, 0.0, 0.0, 10.0, 5.0, 180.0, now, "UTC", now
        );

        cache.put(51.5074, -0.1278, snapshot);

        // Retrieve when fresh
        Optional<WeatherSnapshot> fresh = cache.getIfFresh(51.5074, -0.1278, Duration.ofMinutes(10));
        assertTrue(fresh.isPresent());
        assertEquals(20.0, fresh.get().getTemperature());

        // Expired max age
        Thread.sleep(10);
        Optional<WeatherSnapshot> expired = cache.getIfFresh(51.5074, -0.1278, Duration.ZERO);
        assertTrue(expired.isEmpty());

        // getAny still returns cached value even when expired
        Optional<WeatherSnapshot> any = cache.getAny(51.5074, -0.1278);
        assertTrue(any.isPresent());
        assertEquals(20.0, any.get().getTemperature());
    }

    @Test
    @DisplayName("Verify coordinate matching with rounding precision")
    void testCoordinatePrecisionMatching() {
        WeatherCache cache = new WeatherCache();
        Instant now = Instant.now();

        WeatherSnapshot snapshot = new WeatherSnapshot(
                1, 18.0, null, null, 0.0, 0.0, 0.0, 0.0, 25.0, null, null, now, "UTC", now
        );

        cache.put(51.507400, -0.127800, snapshot);

        // Slightly different coordinate within rounding range
        Optional<WeatherSnapshot> retrieved = cache.getIfFresh(51.507403, -0.127804, Duration.ofMinutes(5));
        assertTrue(retrieved.isPresent());

        // Significantly different coordinate
        Optional<WeatherSnapshot> different = cache.getIfFresh(51.5100, -0.1278, Duration.ofMinutes(5));
        assertTrue(different.isEmpty());
    }
}
