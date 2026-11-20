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
        private boolean lastForceRefresh = false;

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
            return getCurrentWeather(latitude, longitude, false);
        }

        @Override
        public CompletableFuture<WeatherSnapshot> getCurrentWeather(double latitude, double longitude, boolean forceRefresh) {
            this.lastForceRefresh = forceRefresh;
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
                48.8566, 2.3522,
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
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);

        // 1. First sync succeeds
        Instant now = Instant.now();
        WeatherSnapshot snapshot = new WeatherSnapshot(
                51.5074, -0.1278,
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
    @DisplayName("Verify cached weather fallback when initial sync fails for matching location")
    void testCachedWeatherFallbackDuringOutage() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);

        Instant past = Instant.now().minusSeconds(300);
        provider.cachedSnapshot = new WeatherSnapshot(
                51.5074, -0.1278,
                71, -2.0, null, null, 1.0, 0.0, 0.0, 1.0, 100.0, null, null, past, "UTC", past
        );
        provider.nextResult.completeExceptionally(new RuntimeException("Network unreachable"));

        WeatherState state = manager.syncWeather(null, config, false).get();

        assertEquals(WeatherState.SyncStatus.ERROR, state.getSyncStatus());
        assertTrue(state.getLatestSnapshot().isPresent(), "Should pick up cached snapshot if state had none");
        assertEquals(WeatherCondition.SNOW, state.getDesiredCondition());
    }

    @Test
    @DisplayName("Verify cached weather from different coordinates is rejected during outage")
    void testCachedWeatherFromDifferentLocationRejected() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(48.8566);
        config.setLongitude(2.3522); // Paris

        Instant past = Instant.now().minusSeconds(300);
        // Cache contains London data
        provider.cachedSnapshot = new WeatherSnapshot(
                51.5074, -0.1278,
                71, -2.0, null, null, 1.0, 0.0, 0.0, 1.0, 100.0, null, null, past, "UTC", past
        );
        provider.nextResult.completeExceptionally(new RuntimeException("Network unreachable"));

        WeatherState state = manager.syncWeather(null, config, false).get();

        assertEquals(WeatherState.SyncStatus.ERROR, state.getSyncStatus());
        assertTrue(state.getLatestSnapshot().isEmpty(), "Must not apply cached weather from another location");
    }

    @Test
    @DisplayName("Verify stale asynchronous response is dropped when coordinates change in flight")
    void testCoordinateChangeDuringInFlightRequestDropped() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);

        // Start sync (in-flight)
        CompletableFuture<WeatherState> syncFuture = manager.syncWeather(null, config, false);

        // User changes coordinates while request is in flight
        config.setLatitude(35.6762);
        config.setLongitude(139.6503);

        // Now provider completes with old London data
        Instant now = Instant.now();
        provider.nextResult.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                61, 10.0, null, null, 2.0, 2.0, 0.0, 0.0, 100.0, null, null, now, "UTC", now
        ));

        WeatherState state = syncFuture.get();

        // Stale response must be dropped because config coordinates changed
        assertTrue(state.getLatestSnapshot().isEmpty(), "Old location snapshot must not be stored");
        assertEquals(WeatherState.SyncStatus.FETCHING, state.getSyncStatus());
    }

    @Test
    @DisplayName("Verify stale asynchronous response is dropped when mod is disabled in flight")
    void testDisableDuringInFlightRequestDropped() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);

        CompletableFuture<WeatherState> syncFuture = manager.syncWeather(null, config, false);

        // Disable in flight
        config.setEnabled(false);

        Instant now = Instant.now();
        provider.nextResult.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                61, 10.0, null, null, 2.0, 2.0, 0.0, 0.0, 100.0, null, null, now, "UTC", now
        ));

        WeatherState state = syncFuture.get();
        assertTrue(state.getLatestSnapshot().isEmpty());
    }

    @Test
    @DisplayName("Verify invalidateInFlight drops pending async response")
    void testInvalidateInFlightDropsResponse() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);

        CompletableFuture<WeatherState> syncFuture = manager.syncWeather(null, config, false);

        // Invalidate in-flight
        manager.invalidateInFlight();

        Instant now = Instant.now();
        provider.nextResult.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                0, 18.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));

        WeatherState state = syncFuture.get();
        assertTrue(state.getLatestSnapshot().isEmpty());
    }

    @Test
    @DisplayName("Verify forceRefresh flag is forwarded to weather provider")
    void testForceRefreshForwardedToProvider() {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        manager.syncWeather(null, config, true);

        assertTrue(provider.lastForceRefresh, "forceRefresh=true must be forwarded to provider");
    }

    @Test
    @DisplayName("Verify complete weather lifecycle: enable -> sync -> disable -> drop stale -> re-enable -> sync")
    void testCompleteWeatherLifecycle() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);
        config.setEnabled(true);

        // 1. Initial enabled sync
        Instant now = Instant.now();
        WeatherSnapshot snapRain = new WeatherSnapshot(
                51.5074, -0.1278,
                61, 12.0, null, null, 2.0, 2.0, 0.0, 0.0, 90.0, null, null, now, "UTC", now
        );
        provider.nextResult.complete(snapRain);
        WeatherState state1 = manager.syncWeather(null, config, false).get();

        assertEquals(WeatherState.SyncStatus.SUCCESS, state1.getSyncStatus());
        assertEquals(WeatherCondition.RAIN, state1.getCurrentAppliedCondition());
        assertEquals(12.0, state1.getLatestSnapshot().orElseThrow().getTemperature());

        // 2. Start a second in-flight request, then disable synchronization
        provider.nextResult = new CompletableFuture<>();
        CompletableFuture<WeatherState> inFlightFuture = manager.syncWeather(null, config, false);

        config.setEnabled(false);
        manager.invalidateInFlight();

        // 3. Complete the in-flight request while disabled
        WeatherSnapshot snapThunder = new WeatherSnapshot(
                51.5074, -0.1278,
                95, 18.0, null, null, 10.0, 10.0, 0.0, 0.0, 100.0, null, null, now, "UTC", now
        );
        provider.nextResult.complete(snapThunder);
        WeatherState stateDisabled = inFlightFuture.get();

        // Must NOT apply the thunderstorm from the disabled in-flight request
        assertNotEquals(WeatherCondition.THUNDERSTORM, stateDisabled.getCurrentAppliedCondition());
        assertEquals(WeatherCondition.RAIN, stateDisabled.getCurrentAppliedCondition());

        // 4. Attempt sync while disabled -> returns immediately without calling provider
        provider.nextResult = new CompletableFuture<>();
        WeatherState stateSkipped = manager.syncWeather(null, config, false).get();
        assertFalse(provider.nextResult.isDone());

        // 5. Re-enable synchronization and perform fresh sync
        config.setEnabled(true);
        WeatherSnapshot snapClear = new WeatherSnapshot(
                51.5074, -0.1278,
                0, 22.0, null, null, 0.0, 0.0, 0.0, 0.0, 5.0, null, null, now, "UTC", now
        );
        provider.nextResult.complete(snapClear);
        WeatherState stateReEnabled = manager.syncWeather(null, config, true).get();

        assertEquals(WeatherState.SyncStatus.SUCCESS, stateReEnabled.getSyncStatus());
        assertEquals(WeatherCondition.CLEAR, stateReEnabled.getCurrentAppliedCondition());
        assertEquals(22.0, stateReEnabled.getLatestSnapshot().orElseThrow().getTemperature());
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
