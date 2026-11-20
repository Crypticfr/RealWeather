package com.realweather.server;

import com.realweather.RealWeather;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WeatherEnforcementHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherEnforcementHandler.class);

    public static final int ENFORCEMENT_INTERVAL_TICKS = 20; // 1 second (20 server ticks)
    public static final int TIMER_BUFFER_THRESHOLD_TICKS = 600; // 30 seconds

    private WeatherEnforcementHandler() {
    }

    public static boolean isCheckTick(long gameTime) {
        return Math.floorMod(gameTime, ENFORCEMENT_INTERVAL_TICKS) == 0;
    }

    public static boolean shouldEnforce() {
        RealWeather mod = RealWeather.getInstance();
        if (mod == null) {
            return false;
        }
        WeatherConfig config = mod.getConfigManager() != null ? mod.getConfigManager().getConfig() : null;
        WeatherManager manager = mod.getWeatherManager();
        WeatherState state = manager != null ? manager.getWeatherState() : null;

        return shouldEnforce(config, state);
    }

    public static boolean shouldEnforce(WeatherConfig config, WeatherState state) {
        if (config == null || !config.isEnabled()) {
            return false;
        }
        if (state == null || !state.hasValidAppliedWeather()) {
            return false;
        }
        return state.getCurrentAppliedCondition() != null;
    }

    /**
     * Periodic enforcement check executed once every 20 Overworld server ticks.
     * Compares the actual Overworld weather state against the current RealWeather target.
     * Restores the target only when a genuine mismatch or expiring timer is detected.
     */
    public static void checkAndEnforce(MinecraftServer server) {
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null || !WeatherController.isOverworld(overworld.dimension())) {
            return;
        }

        // Fast-path: return immediately on 19 out of 20 ticks
        long gameTime = overworld.getGameTime();
        if (!isCheckTick(gameTime)) {
            return;
        }

        RealWeather mod = RealWeather.getInstance();
        if (mod == null) {
            return;
        }

        WeatherConfig config = mod.getConfigManager() != null ? mod.getConfigManager().getConfig() : null;
        WeatherManager manager = mod.getWeatherManager();
        if (manager == null) {
            return;
        }

        WeatherState state = manager.getWeatherState();
        if (!shouldEnforce(config, state)) {
            return;
        }

        WeatherData data = server.getWeatherData();
        if (data == null) {
            return;
        }

        WeatherCondition condition = state.getCurrentAppliedCondition();
        if (condition == null) {
            return;
        }

        if (isMismatch(condition, config, data.isRaining(), data.isThundering(), data.getRainTime(), data.getClearWeatherTime())) {
            restoreAppliedWeather(server, condition, config);
        }
    }

    public static boolean isMismatch(WeatherCondition condition, WeatherConfig config,
                                     boolean isRaining, boolean isThundering,
                                     int rainTime, int clearWeatherTime) {
        if (condition == null || config == null || !config.isEnabled()) {
            return false;
        }

        int durationTicks = WeatherController.calculateDurationTicks(config);
        WeatherController.TargetWeather target = WeatherController.calculateTargetWeather(condition, config, durationTicks);

        // Check if boolean weather state differs from target
        if (target.raining() != isRaining) {
            return true;
        }
        if (target.thundering() != isThundering) {
            return true;
        }

        // Buffer check: prevent vanilla weather timer from expiring between periodic checks
        if (target.raining() && rainTime > 0 && rainTime <= TIMER_BUFFER_THRESHOLD_TICKS) {
            return true;
        }
        if (!target.raining() && clearWeatherTime > 0 && clearWeatherTime <= TIMER_BUFFER_THRESHOLD_TICKS) {
            return true;
        }

        return false;
    }

    public static void restoreAppliedWeather(MinecraftServer server, WeatherCondition condition, WeatherConfig config) {
        if (server == null || condition == null || config == null) {
            return;
        }
        RealWeather mod = RealWeather.getInstance();
        if (mod == null) {
            return;
        }

        try {
            mod.getWeatherController().applyWeather(server, condition, config);
            LOGGER.info("RealWeather restored Overworld weather to {} (periodic consistency enforcement)", condition);
        } catch (Exception e) {
            LOGGER.error("Failed to restore RealWeather condition during enforcement: {}", e.getMessage(), e);
        }
    }
}
