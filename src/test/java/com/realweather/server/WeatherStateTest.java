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
    @DisplayName("Verify state transitions on success and error")
    void testStateTransitions() {
        WeatherState state = new WeatherState();

        assertEquals(WeatherState.SyncStatus.IDLE, state.getSyncStatus());
        assertEquals(WeatherCondition.CLEAR, state.getCurrentAppliedCondition());
        assertEquals(WeatherCondition.CLEAR, state.getDesiredCondition());
        assertTrue(state.getLatestSnapshot().isEmpty());
        assertTrue(state.getLastError().isEmpty());
        assertTrue(state.isStale(Duration.ofMinutes(15), Instant.now()));

        // Transition to SYNCING
        state.setSyncing();
        assertEquals(WeatherState.SyncStatus.SYNCING, state.getSyncStatus());

        // Transition to SUCCESS
        Instant now = Instant.now();
        WeatherSnapshot snapshot = new WeatherSnapshot(
                61, 14.5, 12.0, 80.0, 2.5, 2.5, 0.0, 0.0, 95.0, 10.0, 180.0, now, "UTC", now
        );
        state.updateSuccess(snapshot, WeatherCondition.RAIN, now);
        state.recordApplied(WeatherCondition.RAIN);

        assertEquals(WeatherState.SyncStatus.SUCCESS, state.getSyncStatus());
        assertEquals(WeatherCondition.RAIN, state.getCurrentAppliedCondition());
        assertEquals(WeatherCondition.RAIN, state.getDesiredCondition());
        assertTrue(state.getLatestSnapshot().isPresent());
        assertTrue(state.getLastError().isEmpty());
        assertFalse(state.isStale(Duration.ofMinutes(15), now.plusSeconds(30)));
        assertTrue(state.isStale(Duration.ofMinutes(15), now.plus(Duration.ofMinutes(20))));

        // Transition to ERROR preserves previous snapshot and applied condition
        state.recordError("API Timeout");
        assertEquals(WeatherState.SyncStatus.ERROR, state.getSyncStatus());
        assertEquals("API Timeout", state.getLastError().orElse(""));
        assertEquals(WeatherCondition.RAIN, state.getCurrentAppliedCondition());
        assertTrue(state.getLatestSnapshot().isPresent(), "Previous valid snapshot should be preserved");
    }
}
