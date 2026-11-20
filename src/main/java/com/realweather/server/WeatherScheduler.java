package com.realweather.server;

import com.realweather.RealWeather;
import com.realweather.config.WeatherConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class WeatherScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherScheduler.class);
    private static final int TICKS_PER_SECOND = 20;
    private static final int LOW_TIMER_THRESHOLD_TICKS = 6000; // 5 minutes

    private final WeatherManager weatherManager;
    private volatile MinecraftServer currentServer;
    private volatile boolean isRunning = false;
    private long tickCounter = 0;
    private long lastAttemptTick = -1;

    public WeatherScheduler(WeatherManager weatherManager) {
        this.weatherManager = Objects.requireNonNull(weatherManager, "weatherManager cannot be null");
    }

    public synchronized void onServerStarted(MinecraftServer server) {
        this.currentServer = server;
        this.isRunning = true;
        this.tickCounter = 0;
        this.lastAttemptTick = -1;

        LOGGER.info("WeatherScheduler started. Performing initial synchronization...");
        triggerSync(true);
    }

    public synchronized void onServerStopping(MinecraftServer server) {
        LOGGER.info("WeatherScheduler stopping...");
        this.isRunning = false;
        this.currentServer = null;
        this.weatherManager.invalidateInFlight();
    }

    public void onServerTick(MinecraftServer server) {
        if (!isRunning || server == null || server != currentServer) {
            return;
        }

        tickCounter++;
        WeatherConfig config = RealWeather.getInstance().getConfigManager().getConfig();
        if (config == null || !config.isEnabled()) {
            return;
        }

        long updateIntervalTicks = (long) config.getUpdateIntervalMinutes() * 60 * TICKS_PER_SECOND;
        long retryIntervalTicks = (long) config.getRetryIntervalSeconds() * TICKS_PER_SECOND;

        boolean isError = weatherManager.getWeatherState().getSyncStatus() == WeatherState.SyncStatus.ERROR;
        long requiredInterval = isError ? retryIntervalTicks : updateIntervalTicks;

        long ticksSinceLastAttempt = lastAttemptTick >= 0 ? tickCounter - lastAttemptTick : Long.MAX_VALUE;

        if (ticksSinceLastAttempt >= requiredInterval) {
            triggerSync(false);
            return;
        }

        // Periodic consistency enforcement (runs once every 20 Overworld ticks)
        WeatherEnforcementHandler.checkAndEnforce(server);
    }

    public void triggerSync(boolean force) {
        if (currentServer == null) {
            return;
        }
        lastAttemptTick = tickCounter;
        WeatherConfig config = RealWeather.getInstance().getConfigManager().getConfig();
        weatherManager.syncWeather(currentServer, config, force);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public long getTickCounter() {
        return tickCounter;
    }
}
