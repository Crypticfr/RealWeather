package com.realweather.server;

import com.realweather.RealWeather;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;

import java.util.Objects;

public class WeatherController {

    public record TargetWeather(int clearTime, int weatherTime, boolean raining, boolean thundering) {
        public static TargetWeather clear(int durationTicks) {
            return new TargetWeather(Math.max(1, durationTicks), 0, false, false);
        }

        public static TargetWeather rain(int durationTicks) {
            return new TargetWeather(0, Math.max(1, durationTicks), true, false);
        }

        public static TargetWeather thunder(int durationTicks) {
            return new TargetWeather(0, Math.max(1, durationTicks), true, true);
        }
    }

    public static boolean isOverworld(ResourceKey<?> dimensionKey) {
        if (dimensionKey == null) {
            return false;
        }
        return "minecraft".equals(dimensionKey.identifier().getNamespace())
                && "overworld".equals(dimensionKey.identifier().getPath());
    }

    public static int calculateDurationTicks(WeatherConfig config) {
        int updateIntervalMinutes = config != null ? config.getUpdateIntervalMinutes() : 15;
        // Provide at least 2x update interval duration buffer (min 20 minutes = 24000 ticks)
        return Math.max(24000, updateIntervalMinutes * 60 * 20 * 2);
    }

    /**
     * Maps real-world WeatherCondition into exactly three Minecraft weather states:
     * 1. CLEAR: Clear, mainly clear, partly cloudy, cloudy, overcast, fog
     * 2. RAIN: Drizzle, rain, heavy rain, snowfall, freezing rain
     * 3. THUNDERSTORM: Thunderstorms
     */
    public static TargetWeather calculateTargetWeather(WeatherCondition condition, WeatherConfig config, int durationTicks) {
        Objects.requireNonNull(condition, "condition cannot be null");
        if (config == null || !config.isEnabled()) {
            return TargetWeather.clear(durationTicks);
        }

        // Thunderstorm synchronization -> THUNDERSTORM
        if (condition.isThunder()) {
            if (config.isSyncThunder()) {
                return TargetWeather.thunder(durationTicks);
            }
            if (config.isSyncRain()) {
                return TargetWeather.rain(durationTicks);
            }
            return TargetWeather.clear(durationTicks);
        }

        // Snowfall synchronization -> RAIN
        // (In vanilla Minecraft, snowfall occurs in cold biomes during precipitation. Biome temperature determines rain vs snow.)
        if (condition.isSnow()) {
            if (config.isSyncSnow()) {
                return TargetWeather.rain(durationTicks);
            }
            return TargetWeather.clear(durationTicks);
        }

        // Precipitation synchronization (drizzle, rain, heavy rain, freezing rain) -> RAIN
        if (condition.isPrecipitating()) {
            if (config.isSyncRain()) {
                return TargetWeather.rain(durationTicks);
            }
            return TargetWeather.clear(durationTicks);
        }

        // Clear, cloudy, overcast, fog -> CLEAR (cloud cover and fog are not treated as precipitation)
        return TargetWeather.clear(durationTicks);
    }

    public void applyWeather(MinecraftServer server, WeatherCondition condition, WeatherConfig config) {
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null || !isOverworld(overworld.dimension()) || !overworld.canHaveWeather()) {
            RealWeather.LOGGER.warn("Cannot apply weather: Overworld unavailable or cannot have weather");
            return;
        }

        int durationTicks = calculateDurationTicks(config);
        TargetWeather target = calculateTargetWeather(condition, config, durationTicks);

        server.setWeatherParameters(target.clearTime(), target.weatherTime(), target.raining(), target.thundering());

        RealWeather.LOGGER.info("Applied weather to Overworld: condition={}, raining={}, thundering={}, duration={} ticks",
                condition, target.raining(), target.thundering(), target.weatherTime() > 0 ? target.weatherTime() : target.clearTime());
    }

    public void restoreVanillaWeather(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld != null && isOverworld(overworld.dimension())) {
            overworld.resetWeatherCycle();
            RealWeather.LOGGER.info("Restored native Minecraft weather cycle to Overworld");
        }
    }

    public WeatherCondition getCurrentWorldCondition(MinecraftServer server) {
        if (server == null) {
            return WeatherCondition.CLEAR;
        }
        WeatherData data = server.getWeatherData();
        if (data == null) {
            return WeatherCondition.CLEAR;
        }
        if (data.isThundering()) {
            return WeatherCondition.THUNDERSTORM;
        }
        if (data.isRaining()) {
            return WeatherCondition.RAIN;
        }
        return WeatherCondition.CLEAR;
    }
}
