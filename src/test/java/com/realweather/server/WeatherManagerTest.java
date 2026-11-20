package com.realweather.server;

import com.realweather.api.WeatherProvider;
import com.realweather.api.WeatherSnapshot;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class WeatherManagerTest {

    private static class MockWeatherProvider implements WeatherProvider {
        private CompletableFuture<WeatherSnapshot> nextResult = new CompletableFuture<>();
        private WeatherSnapshot cachedSnapshot = null;

        @Override
        public String getId() {
            return "mock";
        }

        @Override
        public String getName() {
            return "Mock Provider";
        }

        @Override
        public CompletableFuture<WeatherSnapshot> getCurrentWeather(double latitude, double longitude) {
            return nextResult;
        }

        @Override
        public Optional<WeatherSnapshot> getCachedWeather(double latitude, double longitude) {
            return Optional.ofNullable(cachedSnapshot);
        }
    }

    @Test
    @DisplayName("Verify weather synchronization success updates state")
    void testSuccessfulSynchronization() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLocationName("Paris, France");
        config.setLatitude(48.8566);
        config.setLongitude(2.3522);

        Instant now = Instant.now();
        WeatherSnapshot snapshot = new WeatherSnapshot(
                61, 15.0, 13.0, 70.0, 1.0, 1.0, 0.0, 0.0, 80.0, 8.0, 190.0, now, "Europe/Paris", now
        );
        provider.nextResult.complete(snapshot);

        WeatherState state = manager.syncWeather(null, config, false).get();

        assertEquals(WeatherState.SyncStatus.SUCCESS, state.getSyncStatus());
        assertEquals(WeatherCondition.RAIN, state.getDesiredCondition());
        assertTrue(state.getLatestSnapshot().isPresent());
        assertEquals(15.0, state.getLatestSnapshot().get().getTemperature());
    }

    @Test
    @DisplayName("Verify API failure preserves previous state and logs error")
    void testApiFailurePreservesPreviousState() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();

        // 1. First sync succeeds
        Instant now = Instant.now();
        WeatherSnapshot snapshot = new WeatherSnapshot(
                0, 20.0, null, null, 0.0, 0.0, 0.0, 0.0, 10.0, null, null, now, "UTC", now
        );
        provider.nextResult.complete(snapshot);
        manager.syncWeather(null, config, false).get();

        // 2. Second sync fails
        provider.nextResult = new CompletableFuture<>();
        provider.nextResult.completeExceptionally(new RuntimeException("Connection refused"));

        WeatherState state = manager.syncWeather(null, config, false).get();

        assertEquals(WeatherState.SyncStatus.ERROR, state.getSyncStatus());
        assertEquals("Connection refused", state.getLastError().orElse(""));
        assertTrue(state.getLatestSnapshot().isPresent());
        assertEquals(20.0, state.getLatestSnapshot().get().getTemperature(), "Previous snapshot must be preserved");
    }

    @Test
    @DisplayName("Verify cached weather fallback when initial sync fails")
    void testCachedWeatherFallbackDuringOutage() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();

        Instant past = Instant.now().minusSeconds(300);
        provider.cachedSnapshot = new WeatherSnapshot(
                71, -2.0, null, null, 1.0, 0.0, 0.0, 1.0, 100.0, null, null, past, "UTC", past
        );
        provider.nextResult.completeExceptionally(new RuntimeException("Network unreachable"));

        WeatherState state = manager.syncWeather(null, config, false).get();

        assertEquals(WeatherState.SyncStatus.ERROR, state.getSyncStatus());
        assertTrue(state.getLatestSnapshot().isPresent(), "Should pick up cached snapshot if state had none");
        assertEquals(WeatherCondition.SNOW, state.getDesiredCondition());
    }

    @Test
    @DisplayName("Verify disabled synchronization does not execute sync")
    void testDisabledSyncDoesNotExecute() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setEnabled(false);

        WeatherState state = manager.syncWeather(null, config, false).get();
        assertEquals(WeatherState.SyncStatus.IDLE, state.getSyncStatus());
        assertFalse(provider.nextResult.isDone());
    }
}
