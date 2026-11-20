package com.realweather.server;

import com.realweather.RealWeather;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class WeatherSleepHandler {
    public static final String RESTRICTION_MESSAGE = "You cannot sleep during a real-world thunderstorm.";

    private WeatherSleepHandler() {
    }

    public static void register() {
        EntitySleepEvents.ALLOW_SLEEPING.register(WeatherSleepHandler::onAllowSleeping);
    }

    public static Player.BedSleepingProblem onAllowSleeping(Player player, BlockPos sleepingPos) {
        if (player == null) {
            return null;
        }

        Level level = player.level();
        if (level == null || !WeatherController.isOverworld(level.dimension())) {
            return null;
        }

        RealWeather mod = RealWeather.getInstance();
        if (mod == null) {
            return null;
        }

        WeatherConfig config = mod.getConfigManager().getConfig();
        if (config == null || !config.isEnabled()) {
            return null;
        }

        WeatherManager manager = mod.getWeatherManager();
        if (manager == null) {
            return null;
        }

        WeatherCondition applied = manager.getWeatherState().getCurrentAppliedCondition();
        boolean isDaytime = isDaytime(level);

        if (shouldDenySleep(config.isEnabled(), applied, isDaytime)) {
            return new Player.BedSleepingProblem(Component.literal(RESTRICTION_MESSAGE));
        }

        return null;
    }

    public static boolean shouldDenySleep(boolean modEnabled, WeatherCondition appliedCondition, boolean isDaytime) {
        if (!modEnabled || appliedCondition == null) {
            return false;
        }
        return appliedCondition.isThunder() && isDaytime;
    }

    public static boolean isDaytime(Level level) {
        if (level == null) {
            return false;
        }
        try {
            long clockTime = level.getOverworldClockTime();
            return isDaytime(clockTime);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isDaytime(long clockTime) {
        long timeOfDay = Math.floorMod(clockTime, 24000L);
        return timeOfDay < 12542L || timeOfDay > 23459L;
    }
}
