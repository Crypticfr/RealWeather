package com.realweather.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSnapshotTest {

    @Test
    @DisplayName("Verify snapshot creation and distinguishing unavailable from zero")
    void testSnapshotDistinguishesZeroFromUnavailable() {
        Instant now = Instant.now();

        // Case 1: unavailable values (null)
        WeatherSnapshot snapshotWithNulls = new WeatherSnapshot(
                51.5074, -0.1278,
                0, 15.0, null, null, 0.0, 0.0, 0.0, 0.0, 10.0, null, null, now, "UTC", now
        );

        assertEquals(51.5074, snapshotWithNulls.getLatitude(), 0.0001);
        assertEquals(-0.1278, snapshotWithNulls.getLongitude(), 0.0001);
        assertTrue(snapshotWithNulls.matchesLocation(51.5074, -0.1278));
        assertFalse(snapshotWithNulls.matchesLocation(48.8566, 2.3522));

        assertTrue(snapshotWithNulls.getApparentTemperature().isEmpty());
        assertTrue(snapshotWithNulls.getRelativeHumidity().isEmpty());
        assertTrue(snapshotWithNulls.getWindSpeed().isEmpty());
        assertTrue(snapshotWithNulls.getWindDirection().isEmpty());

        // Case 2: actual zero values
        WeatherSnapshot snapshotWithZeros = new WeatherSnapshot(
                0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, now, "UTC", now
        );

        assertEquals(0.0, snapshotWithZeros.getLatitude());
        assertEquals(0.0, snapshotWithZeros.getLongitude());
        assertTrue(snapshotWithZeros.getApparentTemperature().isPresent());
        assertEquals(0.0, snapshotWithZeros.getApparentTemperature().getAsDouble());

        assertTrue(snapshotWithZeros.getRelativeHumidity().isPresent());
        assertEquals(0.0, snapshotWithZeros.getRelativeHumidity().getAsDouble());

        assertTrue(snapshotWithZeros.getWindSpeed().isPresent());
        assertEquals(0.0, snapshotWithZeros.getWindSpeed().getAsDouble());

        assertTrue(snapshotWithZeros.getWindDirection().isPresent());
        assertEquals(0.0, snapshotWithZeros.getWindDirection().getAsDouble());
    }

    @Test
    @DisplayName("Verify validation rejects NaN, infinite, out-of-range coordinates, negative precipitation or weather codes")
    void testValidationRejectsInvalidValues() {
        Instant now = Instant.now();

        // NaN and Infinite coordinates
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                Double.NaN, 0.0, 0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                0.0, Double.NaN, 0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                Double.POSITIVE_INFINITY, 0.0, 0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                0.0, Double.NEGATIVE_INFINITY, 0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                95.0, 0.0, 0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                0.0, -185.0, 0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));

        // Weather codes and precip
        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                -1, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));

        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                0, 10.0, null, null, -1.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));

        assertThrows(IllegalArgumentException.class, () -> new WeatherSnapshot(
                0, 10.0, null, null, 0.0, -0.5, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));

        assertThrows(NullPointerException.class, () -> new WeatherSnapshot(
                0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, null, "UTC", now
        ));

        assertThrows(NullPointerException.class, () -> new WeatherSnapshot(
                0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, null, now
        ));
    }

    @Test
    @DisplayName("Verify cloud cover is clamped between 0 and 100")
    void testCloudCoverClamping() {
        Instant now = Instant.now();

        WeatherSnapshot high = new WeatherSnapshot(
                0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, 150.0, null, null, now, "UTC", now
        );
        assertEquals(100.0, high.getCloudCover());

        WeatherSnapshot low = new WeatherSnapshot(
                0, 10.0, null, null, 0.0, 0.0, 0.0, 0.0, -20.0, null, null, now, "UTC", now
        );
        assertEquals(0.0, low.getCloudCover());
    }
}
