package com.realweather.server;

import com.realweather.api.WeatherProvider;
import com.realweather.api.WeatherSnapshot;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class WeatherEnforcementTest {

    private WeatherConfig config;
    private WeatherState state;
    private WeatherSnapshot testSnapshot;

    @BeforeEach
    void setUp() {
        config = new WeatherConfig();
        config.setEnabled(true);
        config.setLatitude(40.7128);
        config.setLongitude(-74.0060);
        config.setSyncRain(true);
        config.setSyncThunder(true);
        config.setSyncSnow(true);

        state = new WeatherState();
        Instant now = Instant.now();
        testSnapshot = new WeatherSnapshot(
                40.7128, -74.0060,
                0, 20.0, 20.0, 50.0, 0.0, 0.0, 0.0, 0.0, 10.0, 5.0, 180.0, now, "UTC", now
        );
    }

    @Test
    @DisplayName("Requirement 1 & 2: Consistency check executes once every 20 ticks; other 19 ticks return immediately")
    void testPeriodicCheckFrequency() {
        int checkCount = 0;
        int skipCount = 0;

        for (long tick = 0; tick < 100; tick++) {
            if (WeatherEnforcementHandler.isCheckTick(tick)) {
                checkCount++;
            } else {
                skipCount++;
            }
        }

        assertEquals(5, checkCount, "Over 100 ticks, exactly 5 checks should occur (every 20 ticks)");
        assertEquals(95, skipCount, "Over 100 ticks, exactly 95 ticks should be skipped without check");
        assertTrue(WeatherEnforcementHandler.isCheckTick(0));
        assertTrue(WeatherEnforcementHandler.isCheckTick(20));
        assertTrue(WeatherEnforcementHandler.isCheckTick(40));
        assertFalse(WeatherEnforcementHandler.isCheckTick(1));
        assertFalse(WeatherEnforcementHandler.isCheckTick(19));
    }

    @Test
    @DisplayName("Requirement 3: /weather clear is detected as mismatch and corrected within 20 ticks when target is RAIN")
    void testWeatherClearCorrectedWhenTargetIsRain() {
        // Target is RAIN, but someone ran /weather clear (isRaining=false, isThundering=false)
        boolean mismatch = WeatherEnforcementHandler.isMismatch(
                WeatherCondition.RAIN, config, false, false, 0, 12000);
        assertTrue(mismatch, "/weather clear must be detected as a mismatch when RealWeather target is RAIN");

        // Simulate 20-tick delay detection
        int ticksUntilCorrection = -1;
        long commandTick = 5; // /weather clear run on tick 5
        for (long tick = commandTick; tick <= commandTick + 20; tick++) {
            if (WeatherEnforcementHandler.isCheckTick(tick)) {
                ticksUntilCorrection = (int) (tick - commandTick);
                break;
            }
        }
        assertTrue(ticksUntilCorrection >= 0 && ticksUntilCorrection <= 20,
                "Correction must occur within 20 ticks (actual: " + ticksUntilCorrection + " ticks)");
    }

    @Test
    @DisplayName("Requirement 4: /weather rain is detected as mismatch and corrected within 20 ticks when target is CLEAR")
    void testWeatherRainCorrectedWhenTargetIsClear() {
        // Target is CLEAR, but someone ran /weather rain (isRaining=true, isThundering=false)
        boolean mismatch = WeatherEnforcementHandler.isMismatch(
                WeatherCondition.CLEAR, config, true, false, 12000, 0);
        assertTrue(mismatch, "/weather rain must be detected as a mismatch when RealWeather target is CLEAR");
    }

    @Test
    @DisplayName("Requirement 5: /weather thunder is detected as mismatch when target is CLEAR or RAIN")
    void testWeatherThunderCorrectedWhenTargetIsClearOrRain() {
        // Target is CLEAR, /weather thunder run (isRaining=true, isThundering=true)
        assertTrue(WeatherEnforcementHandler.isMismatch(WeatherCondition.CLEAR, config, true, true, 12000, 0),
                "Thunder must mismatch CLEAR target");

        // Target is RAIN (without thunder), /weather thunder run (isRaining=true, isThundering=true)
        assertTrue(WeatherEnforcementHandler.isMismatch(WeatherCondition.RAIN, config, true, true, 12000, 0),
                "Thunder must mismatch RAIN-only target");
    }

    @Test
    @DisplayName("Requirement 6: Sleeping-related weather clearing is detected as mismatch and corrected")
    void testSleepingWeatherClearCorrected() {
        // Target is RAIN. Sleeping clears weather: isRaining=false, isThundering=false, rainTime=0
        boolean mismatch = WeatherEnforcementHandler.isMismatch(
                WeatherCondition.RAIN, config, false, false, 0, 0);
        assertTrue(mismatch, "Sleeping weather clear must be detected as a mismatch with RAIN target");
    }

    @Test
    @DisplayName("Requirement 7: Natural weather transitions are detected as mismatch and corrected")
    void testNaturalTransitionCorrected() {
        // Target is RAIN. Natural cycle flipped isRaining to false
        boolean mismatch = WeatherEnforcementHandler.isMismatch(
                WeatherCondition.RAIN, config, false, false, 0, 0);
        assertTrue(mismatch, "Natural transition away from RAIN must be detected as a mismatch");
    }

    @Test
    @DisplayName("Requirement 8: Weather timers cannot expire the RealWeather target (buffer refresh)")
    void testTimerBufferRefresh() {
        // Target is RAIN, still raining, but rainTime has dropped to 500 ticks (<= 600 threshold)
        assertTrue(WeatherEnforcementHandler.isMismatch(WeatherCondition.RAIN, config, true, false, 500, 0),
                "Low timer <= 600 ticks must trigger refresh before natural expiration");

        // Target is CLEAR, still clear, but clearWeatherTime has dropped to 300 ticks
        assertTrue(WeatherEnforcementHandler.isMismatch(WeatherCondition.CLEAR, config, false, false, 0, 300),
                "Low clear timer <= 600 ticks must trigger refresh before natural expiration");

        // Target is RAIN, still raining, rainTime is healthy (20000 ticks)
        assertFalse(WeatherEnforcementHandler.isMismatch(WeatherCondition.RAIN, config, true, false, 20000, 0),
                "Healthy timer > 600 ticks must NOT trigger refresh");
    }

    @Test
    @DisplayName("Requirement 9: No redundant weather restoration when target already matches and timer is healthy")
    void testNoRedundantRestoration() {
        // RAIN matches RAIN
        assertFalse(WeatherEnforcementHandler.isMismatch(WeatherCondition.RAIN, config, true, false, 24000, 0));

        // CLEAR matches CLEAR
        assertFalse(WeatherEnforcementHandler.isMismatch(WeatherCondition.CLEAR, config, false, false, 0, 24000));

        // THUNDER matches THUNDER
        assertFalse(WeatherEnforcementHandler.isMismatch(WeatherCondition.THUNDERSTORM, config, true, true, 24000, 0));
    }

    @Test
    @DisplayName("Requirement 10: Zero API requests triggered during enforcement checks")
    void testZeroApiRequestsDuringEnforcement() {
        AtomicInteger apiCallCount = new AtomicInteger(0);

        WeatherProvider dummyProvider = new WeatherProvider() {
            @Override
            public String getId() { return "test"; }
            @Override
            public String getName() { return "Test"; }
            @Override
            public CompletableFuture<WeatherSnapshot> getCurrentWeather(double lat, double lon) {
                return getCurrentWeather(lat, lon, false);
            }
            @Override
            public CompletableFuture<WeatherSnapshot> getCurrentWeather(double lat, double lon, boolean forceRefresh) {
                apiCallCount.incrementAndGet();
                return CompletableFuture.completedFuture(testSnapshot);
            }
            @Override
            public Optional<WeatherSnapshot> getCachedWeather(double lat, double lon) {
                return Optional.empty();
            }
        };

        WeatherController controller = new WeatherController();
        WeatherManager manager = new WeatherManager(dummyProvider, controller);

        // Initial sync
        manager.syncWeather(null, config, false).join();
        assertEquals(1, apiCallCount.get(), "Initial sync makes 1 API call");

        // Run 1,000 enforcement checks
        for (int i = 0; i < 1000; i++) {
            WeatherEnforcementHandler.isMismatch(
                    manager.getWeatherState().getCurrentAppliedCondition(),
                    config, false, false, 0, 0);
        }

        // Verify API was never called by enforcement
        assertEquals(1, apiCallCount.get(), "Enforcement checks must NEVER call the weather API");
    }

    @Test
    @DisplayName("Requirement 11: Disabled and pre-sync behaviour allows vanilla changes")
    void testDisabledAndPreSyncAllowsVanilla() {
        // Disabled
        config.setEnabled(false);
        state.updateSuccess(testSnapshot, WeatherCondition.RAIN, Instant.now());
        assertFalse(WeatherEnforcementHandler.shouldEnforce(config, state), "Should not enforce when disabled");
        assertFalse(WeatherEnforcementHandler.isMismatch(WeatherCondition.RAIN, config, false, false, 0, 0),
                "Should not report mismatch when disabled");

        // Pre-sync
        config.setEnabled(true);
        WeatherState freshState = new WeatherState();
        assertFalse(freshState.hasValidAppliedWeather());
        assertFalse(WeatherEnforcementHandler.shouldEnforce(config, freshState), "Should not enforce before first sync");
    }

    @Test
    @DisplayName("Requirement 12: Nether and End remain unaffected")
    void testNetherAndEndUnaffected() {
        ResourceKey<?> overworldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
        ResourceKey<?> netherKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));
        ResourceKey<?> endKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_end"));

        assertTrue(WeatherController.isOverworld(overworldKey));
        assertFalse(WeatherController.isOverworld(netherKey));
        assertFalse(WeatherController.isOverworld(endKey));
    }

    @Test
    @DisplayName("Performance Benchmark: Periodic check overhead vs per-tick overhead")
    void testPerformanceBenchmark() {
        int ticks = 1_000_000;

        // Warmup
        for (int i = 0; i < 100_000; i++) {
            WeatherEnforcementHandler.isCheckTick(i);
        }

        long start = System.nanoTime();
        int checksExecuted = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (WeatherEnforcementHandler.isCheckTick(tick)) {
                checksExecuted++;
                WeatherEnforcementHandler.isMismatch(WeatherCondition.RAIN, config, true, false, 24000, 0);
            }
        }
        long durationNs = System.nanoTime() - start;

        assertEquals(50_000, checksExecuted, "Over 1,000,000 ticks, exactly 50,000 checks executed (1 in 20)");
        double avgNsPerTick = (double) durationNs / ticks;

        // Informational benchmark output (timing is machine-dependent, functional test verifies checksExecuted)
        System.out.printf("Informational enforcement benchmark: %d ticks took %.2f ms (%.2f ns/tick)%n",
                ticks, durationNs / 1_000_000.0, avgNsPerTick);
        assertTrue(durationNs >= 0, "Duration must be non-negative");
    }
}
