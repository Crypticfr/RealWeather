package com.realweather.server;

import com.realweather.RealWeather;
import com.realweather.api.WeatherProvider;
import com.realweather.api.WeatherSnapshot;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import com.realweather.weather.WeatherMapper;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class WeatherManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherManager.class);

    private final WeatherProvider weatherProvider;
    private final WeatherController weatherController;
    private final WeatherState weatherState;
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    public WeatherManager(WeatherProvider weatherProvider, WeatherController weatherController) {
        this.weatherProvider = Objects.requireNonNull(weatherProvider, "weatherProvider cannot be null");
        this.weatherController = Objects.requireNonNull(weatherController, "weatherController cannot be null");
        this.weatherState = new WeatherState();
    }

    public CompletableFuture<WeatherState> syncWeather(MinecraftServer server, WeatherConfig config, boolean forceRefresh) {
        if (config == null || !config.isEnabled()) {
            LOGGER.debug("Skipping weather sync: RealWeather is disabled");
            return CompletableFuture.completedFuture(weatherState);
        }

        if (!isSyncing.compareAndSet(false, true)) {
            LOGGER.debug("Synchronization already in progress, skipping overlapping request");
            return CompletableFuture.completedFuture(weatherState);
        }

        weatherState.setSyncing();
        double lat = config.getLatitude();
        double lon = config.getLongitude();

        return weatherProvider.getCurrentWeather(lat, lon)
                .thenApply(snapshot -> {
                    WeatherCondition desired = WeatherMapper.mapSnapshot(snapshot);
                    weatherState.updateSuccess(snapshot, desired, Instant.now());

                    if (server != null) {
                        server.execute(() -> {
                            weatherController.applyWeather(server, desired, config);
                            weatherState.recordApplied(desired);
                        });
                    }

                    LOGGER.info("Weather synchronized successfully for {} [lat: {}, lon: {}]: condition={}, temp={}°C",
                            config.getLocationName(), lat, lon, desired, snapshot.getTemperature());
                    return weatherState;
                })
                .exceptionally(throwable -> {
                    String errorMsg = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                    LOGGER.warn("Weather synchronization failed for {}: {}. Preserving current world weather.",
                            config.getLocationName(), errorMsg);
                    weatherState.recordError(errorMsg);

                    // Check if stale cached weather is available when no current snapshot exists
                    if (weatherState.getLatestSnapshot().isEmpty()) {
                        Optional<WeatherSnapshot> cached = weatherProvider.getCachedWeather(lat, lon);
                        cached.ifPresent(snapshot -> {
                            WeatherCondition desired = WeatherMapper.mapSnapshot(snapshot);
                            weatherState.updateFromCache(snapshot, desired);
                            if (server != null) {
                                server.execute(() -> {
                                    weatherController.applyWeather(server, desired, config);
                                    weatherState.recordApplied(desired);
                                });
                            }
                            LOGGER.info("Applied stale cached weather during API outage: condition={}", desired);
                        });
                    }

                    return weatherState;
                })
                .whenComplete((state, err) -> isSyncing.set(false));
    }

    public WeatherState getWeatherState() {
        return weatherState;
    }

    public WeatherProvider getWeatherProvider() {
        return weatherProvider;
    }

    public WeatherController getWeatherController() {
        return weatherController;
    }

    public boolean isSyncing() {
        return isSyncing.get();
    }
}
