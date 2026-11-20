package com.realweather.server;

import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeatherEnforcementMeasurementTest {

    @Test
    @DisplayName("Measure Old vs New Enforcement Performance over 1,000,000 ticks")
    void benchmarkOldVsNew() {
        WeatherConfig config = new WeatherConfig();
        config.setEnabled(true);
        config.setUpdateIntervalMinutes(15);
        WeatherCondition condition = WeatherCondition.RAIN;

        int totalTicks = 1_000_000;

        // Warmup JIT
        for (int i = 0; i < 200_000; i++) {
            boolean check = WeatherEnforcementHandler.isCheckTick(i);
            if (check) {
                WeatherEnforcementHandler.isMismatch(condition, config, true, false, 24000, 0);
            }
        }

        // --- Benchmark OLD Per-Tick Architecture ---
        // In the old architecture, isMismatch / isWeatherConflicting was evaluated EVERY tick (1,000,000 times)
        long oldStartNs = System.nanoTime();
        int oldEvaluations = 0;
        for (int tick = 0; tick < totalTicks; tick++) {
            oldEvaluations++;
            WeatherEnforcementHandler.isMismatch(condition, config, true, false, 24000, 0);
        }
        long oldDurationNs = System.nanoTime() - oldStartNs;

        // --- Benchmark NEW 20-Tick Periodic Architecture ---
        // In the new architecture, 19 of 20 ticks exit immediately at the modulo check;
        // only 1 in 20 ticks (50,000 times) evaluates isMismatch
        long newStartNs = System.nanoTime();
        int newEvaluations = 0;
        for (int tick = 0; tick < totalTicks; tick++) {
            if (WeatherEnforcementHandler.isCheckTick(tick)) {
                newEvaluations++;
                WeatherEnforcementHandler.isMismatch(condition, config, true, false, 24000, 0);
            }
        }
        long newDurationNs = System.nanoTime() - newStartNs;

        double oldTotalMs = oldDurationNs / 1_000_000.0;
        double newTotalMs = newDurationNs / 1_000_000.0;

        double oldNsPerTick = (double) oldDurationNs / totalTicks;
        double newNsPerTick = (double) newDurationNs / totalTicks;

        double speedup = (double) oldDurationNs / newDurationNs;

        System.out.println("=================================================================");
        System.out.println("     REALWEATHER ENFORCEMENT BENCHMARK (1,000,000 TICKS)        ");
        System.out.println("=================================================================");
        System.out.printf("Old Architecture (Per-tick):    Total: %.2f ms | Avg: %.2f ns/tick | Evaluations: %d%n",
                oldTotalMs, oldNsPerTick, oldEvaluations);
        System.out.printf("New Architecture (Periodic 20t): Total: %.2f ms | Avg: %.2f ns/tick | Evaluations: %d%n",
                newTotalMs, newNsPerTick, newEvaluations);
        System.out.printf("Invocations per second (20 tps): Old: 20.0/s | New: 1.0/s (95.0%% reduction)%n");
        System.out.printf("Speedup factor:                  %.2fx faster CPU throughput%n", speedup);
        System.out.println("=================================================================");

        assertTrue(newEvaluations < oldEvaluations);
        assertTrue(newEvaluations == 50_000);
    }
}
