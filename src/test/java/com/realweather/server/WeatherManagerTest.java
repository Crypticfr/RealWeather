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
        private final java.util.Queue<CompletableFuture<WeatherSnapshot>> queuedResults = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private CompletableFuture<WeatherSnapshot> nextResult = new CompletableFuture<>();
        private WeatherSnapshot cachedSnapshot = null;
        private boolean lastForceRefresh = false;
        private final java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);

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
            callCount.incrementAndGet();
            CompletableFuture<WeatherSnapshot> queued = queuedResults.poll();
            return queued != null ? queued : nextResult;
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

    @Test
    @DisplayName("Regression: /realweather reload during in-flight request invalidates it and performs fresh sync")
    void testReloadDuringInFlightRequestPerformsFreshSync() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        CompletableFuture<WeatherSnapshot> firstRequestFuture = new CompletableFuture<>();
        CompletableFuture<WeatherSnapshot> secondRequestFuture = new CompletableFuture<>();
        provider.queuedResults.add(firstRequestFuture);
        provider.queuedResults.add(secondRequestFuture);

        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLocationName("London");
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);
        config.setEnabled(true);

        // 1. Initial request starts (in flight)
        CompletableFuture<WeatherState> firstSyncFuture = manager.syncWeather(null, config, false);
        assertTrue(manager.isSyncing(), "Manager must be syncing while first request is in flight");
        assertEquals(1, provider.callCount.get(), "Provider should have received 1 request");

        // 2. User reloads config while first request is still in flight (e.g. coordinates changed to Paris)
        manager.invalidateInFlight();
        assertFalse(manager.isSyncing(), "isSyncing must be false after invalidating in-flight generation");

        config.setLocationName("Paris");
        config.setLatitude(48.8566);
        config.setLongitude(2.3522);

        // 3. Post-reload sync is triggered immediately
        CompletableFuture<WeatherState> secondSyncFuture = manager.syncWeather(null, config, true);
        assertTrue(manager.isSyncing(), "Manager must be syncing for the new post-reload generation");
        assertEquals(2, provider.callCount.get(), "Second request must be dispatched without being blocked by old request");

        // 4. Old request completes now with London data
        Instant now = Instant.now();
        firstRequestFuture.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                61, 10.0, null, null, 2.0, 2.0, 0.0, 0.0, 90.0, null, null, now, "UTC", now
        ));
        WeatherState firstState = firstSyncFuture.get();

        // Old request must be dropped and must not update state or reset isSyncing for the active second request
        assertTrue(firstState.getLatestSnapshot().isEmpty(), "Old request data must not be stored");
        assertTrue(manager.isSyncing(), "Completing invalidated old request must not reset isSyncing for active request");

        // 5. New post-reload request completes with Paris data
        secondRequestFuture.complete(new WeatherSnapshot(
                48.8566, 2.3522,
                0, 25.0, null, null, 0.0, 0.0, 0.0, 0.0, 10.0, null, null, now, "UTC", now
        ));
        WeatherState secondState = secondSyncFuture.get();

        // Fresh request data must be applied
        assertEquals(WeatherState.SyncStatus.SUCCESS, secondState.getSyncStatus());
        assertEquals(WeatherCondition.CLEAR, secondState.getCurrentAppliedCondition());
        assertEquals(25.0, secondState.getLatestSnapshot().orElseThrow().getTemperature());
        assertEquals(48.8566, secondState.getLatestSnapshot().orElseThrow().getLatitude());
        assertFalse(manager.isSyncing(), "Manager isSyncing must be false once active request completes");
    }

    @Test
    @DisplayName("Regression: /realweather enable after invalidation during in-flight request starts fresh sync")
    void testEnableAfterInvalidationDuringInFlightRequestStartsFreshSync() throws Exception {
        MockWeatherProvider provider = new MockWeatherProvider();
        CompletableFuture<WeatherSnapshot> firstRequestFuture = new CompletableFuture<>();
        CompletableFuture<WeatherSnapshot> secondRequestFuture = new CompletableFuture<>();
        provider.queuedResults.add(firstRequestFuture);
        provider.queuedResults.add(secondRequestFuture);

        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setLatitude(51.5074);
        config.setLongitude(-0.1278);
        config.setEnabled(true);

        // 1. Initial sync starts
        CompletableFuture<WeatherState> firstSync = manager.syncWeather(null, config, false);
        assertTrue(manager.isSyncing());
        assertEquals(1, provider.callCount.get());

        // 2. Mod disabled while first request is in flight
        manager.invalidateInFlight();
        config.setEnabled(false);

        // 3. Mod immediately re-enabled (e.g. /realweather enable)
        manager.invalidateInFlight();
        config.setEnabled(true);
        CompletableFuture<WeatherState> secondSync = manager.syncWeather(null, config, true);

        assertEquals(2, provider.callCount.get(), "Re-enabling must trigger a fresh request");
        assertTrue(manager.isSyncing());

        // 4. Old request finishes with THUNDERSTORM
        Instant now = Instant.now();
        firstRequestFuture.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                95, 18.0, null, null, 10.0, 10.0, 0.0, 0.0, 100.0, null, null, now, "UTC", now
        ));
        firstSync.get();
        assertNotEquals(WeatherCondition.THUNDERSTORM, manager.getWeatherState().getCurrentAppliedCondition(),
                "Stale request must not apply thunderstorm");
        assertTrue(manager.isSyncing(), "Old request completion must not clear active status of re-enabled request");

        // 5. Fresh re-enabled request finishes with CLEAR
        secondRequestFuture.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                0, 22.0, null, null, 0.0, 0.0, 0.0, 0.0, 5.0, null, null, now, "UTC", now
        ));
        secondSync.get();
        assertEquals(WeatherCondition.CLEAR, manager.getWeatherState().getCurrentAppliedCondition());
        assertFalse(manager.isSyncing());
    }

    @Test
    @DisplayName("Regression: Overlapping sync requests for the same active generation are safely rejected")
    void testOverlappingSyncRejectedForSameGeneration() {
        MockWeatherProvider provider = new MockWeatherProvider();
        CompletableFuture<WeatherSnapshot> inFlightFuture = new CompletableFuture<>();
        provider.queuedResults.add(inFlightFuture);

        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(provider, controller);

        WeatherConfig config = new WeatherConfig();
        config.setEnabled(true);

        // 1. First sync starts
        CompletableFuture<WeatherState> sync1 = manager.syncWeather(null, config, false);
        assertTrue(manager.isSyncing());
        assertEquals(1, provider.callCount.get());

        // 2. Overlapping sync attempted while first is in flight
        CompletableFuture<WeatherState> sync2 = manager.syncWeather(null, config, false);
        assertEquals(1, provider.callCount.get(), "Overlapping call must be skipped without dispatching new request");
        assertTrue(sync2.isDone(), "Overlapping call should return an immediately completed future");

        // 3. Complete first sync
        Instant now = Instant.now();
        inFlightFuture.complete(new WeatherSnapshot(
                51.5074, -0.1278,
                0, 20.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, now, "UTC", now
        ));
        sync1.join();
        assertFalse(manager.isSyncing());

        // 4. Now another sync can proceed normally
        CompletableFuture<WeatherSnapshot> subsequentFuture = new CompletableFuture<>();
        provider.queuedResults.add(subsequentFuture);
        CompletableFuture<WeatherState> sync3 = manager.syncWeather(null, config, false);
        assertEquals(2, provider.callCount.get(), "Subsequent sync after completion must dispatch normally");
        assertTrue(manager.isSyncing());
    }
}
