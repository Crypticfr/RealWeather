package com.realweather.server;

import com.realweather.api.WeatherProvider;
import com.realweather.api.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSchedulerTest {

    private static class DummyProvider implements WeatherProvider {
        @Override
        public String getId() { return "dummy"; }
        @Override
        public String getName() { return "Dummy"; }
        @Override
        public CompletableFuture<WeatherSnapshot> getCurrentWeather(double lat, double lon) {
            return new CompletableFuture<>();
        }
        @Override
        public Optional<WeatherSnapshot> getCachedWeather(double lat, double lon) {
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("Verify scheduler start and stop lifecycle")
    void testSchedulerLifecycle() {
        WeatherManager manager = new WeatherManager(new DummyProvider(), new WeatherController());
        WeatherScheduler scheduler = new WeatherScheduler(manager);

        assertFalse(scheduler.isRunning());
        assertEquals(0, scheduler.getTickCounter());

        // Stopping when not started
        scheduler.onServerStopping(null);
        assertFalse(scheduler.isRunning());
    }
}
