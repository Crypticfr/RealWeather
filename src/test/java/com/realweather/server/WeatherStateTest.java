package com.realweather.server;

import com.realweather.api.WeatherSnapshot;
import com.realweather.weather.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WeatherStateTest {

    @Test
    @DisplayName("Verify phased state transitions: IDLE -> FETCHING -> FETCHED -> SUCCESS -> ERROR")
    void testPhasedStateTransitions() {
        WeatherState state = new WeatherState();

        assertEquals(WeatherState.SyncStatus.IDLE, state.getSyncStatus());
        assertEquals(WeatherCondition.CLEAR, state.getCurrentAppliedCondition());
        assertEquals(WeatherCondition.CLEAR, state.getDesiredCondition());
        assertTrue(state.getLatestSnapshot().isEmpty());
        assertTrue(state.getLastError().isEmpty());
        assertTrue(state.isStale(Duration.ofMinutes(15), Instant.now()));

        // Phase 1: Fetching
        state.recordFetching();
        assertEquals(WeatherState.SyncStatus.FETCHING, state.getSyncStatus());

        // Phase 2: Fetched from API (world not yet updated)
        Instant fetchTime = Instant.now();
        WeatherSnapshot snapshot = new WeatherSnapshot(
                51.5074, -0.1278,
                61, 14.5, 12.0, 80.0, 2.5, 2.5, 0.0, 0.0, 95.0, 10.0, 180.0, fetchTime, "UTC", fetchTime
        );
        state.recordFetched(snapshot, WeatherCondition.RAIN, fetchTime);

        assertEquals(WeatherState.SyncStatus.FETCHED, state.getSyncStatus(), "Status should be FETCHED, not SUCCESS yet");
        assertEquals(WeatherCondition.CLEAR, state.getCurrentAppliedCondition(), "World condition not applied yet");
        assertEquals(WeatherCondition.RAIN, state.getDesiredCondition());
        assertTrue(state.getLatestSnapshot().isPresent());
        assertTrue(state.getLastFetchTimestamp().isPresent());
        assertTrue(state.getLastApplyTimestamp().isEmpty());

        // Phase 3: Applied on Minecraft server thread
        Instant applyTime = Instant.now();
        state.recordApplied(WeatherCondition.RAIN, applyTime);

        assertEquals(WeatherState.SyncStatus.SUCCESS, state.getSyncStatus());
        assertEquals(WeatherCondition.RAIN, state.getCurrentAppliedCondition());
        assertTrue(state.getLastApplyTimestamp().isPresent());
        assertFalse(state.isStale(Duration.ofMinutes(15), applyTime.plusSeconds(30)));
        assertTrue(state.isStale(Duration.ofMinutes(15), applyTime.plus(Duration.ofMinutes(20))));

        // Phase 4: Error preserves previous state and snapshot
        state.recordError("API Timeout");
        assertEquals(WeatherState.SyncStatus.ERROR, state.getSyncStatus());
        assertEquals("API Timeout", state.getLastError().orElse(""));
        assertEquals(WeatherCondition.RAIN, state.getCurrentAppliedCondition());
        assertTrue(state.getLatestSnapshot().isPresent(), "Previous valid snapshot should be preserved");
    }

    @Test
    @DisplayName("Verify location matching on WeatherState")
    void testLocationMatching() {
        WeatherState state = new WeatherState();
        assertFalse(state.matchesLocation(51.5074, -0.1278));

        Instant now = Instant.now();
        WeatherSnapshot snapshot = new WeatherSnapshot(
                51.5074, -0.1278,
                0, 20.0, null, null, 0.0, 0.0, 0.0, 0.0, 10.0, null, null, now, "UTC", now
        );
        state.recordFetched(snapshot, WeatherCondition.CLEAR, now);

        assertTrue(state.matchesLocation(51.5074, -0.1278));
        assertFalse(state.matchesLocation(48.8566, 2.3522));

        assertTrue(state.getLatestSnapshotForLocation(51.5074, -0.1278).isPresent());
        assertTrue(state.getLatestSnapshotForLocation(48.8566, 2.3522).isEmpty());
    }
}
