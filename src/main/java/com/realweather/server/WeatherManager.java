package com.realweather.server;

import com.realweather.api.WeatherProvider;
import com.realweather.api.WeatherSnapshot;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import com.realweather.weather.WeatherMapper;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class WeatherManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherManager.class);

    private final WeatherProvider weatherProvider;
    private final WeatherController weatherController;
    private final WeatherState weatherState;
    private final Object syncLock = new Object();
    private final AtomicLong syncGeneration = new AtomicLong(0);
    private final AtomicLong activeSyncGeneration = new AtomicLong(-1);

    public WeatherManager(WeatherProvider weatherProvider, WeatherController weatherController) {
        this.weatherProvider = Objects.requireNonNull(weatherProvider, "weatherProvider cannot be null");
        this.weatherController = Objects.requireNonNull(weatherController, "weatherController cannot be null");
        this.weatherState = new WeatherState();
    }

    public long invalidateInFlight() {
        synchronized (syncLock) {
            long newGen = syncGeneration.incrementAndGet();
            LOGGER.debug("Invalidated in-flight weather requests, new generation: {}", newGen);
            return newGen;
        }
    }

    public long getSyncGeneration() {
        return syncGeneration.get();
    }

    public CompletableFuture<WeatherState> syncWeather(MinecraftServer server, WeatherConfig config, boolean forceRefresh) {
        if (config == null || !config.isEnabled()) {
            LOGGER.debug("Skipping weather sync: RealWeather is disabled");
            return CompletableFuture.completedFuture(weatherState);
        }

        final long currentGen;
        synchronized (syncLock) {
            long currentActive = activeSyncGeneration.get();
            long currentTarget = syncGeneration.get();
            if (currentActive != -1 && currentActive == currentTarget) {
                LOGGER.debug("Synchronization already in progress for generation {}, skipping overlapping request", currentTarget);
                return CompletableFuture.completedFuture(weatherState);
            }
            currentGen = syncGeneration.incrementAndGet();
            activeSyncGeneration.set(currentGen);
        }

        final double requestLat = config.getLatitude();
        final double requestLon = config.getLongitude();

        weatherState.recordFetching();

        return weatherProvider.getCurrentWeather(requestLat, requestLon, forceRefresh)
                .thenApply(snapshot -> {
                    if (currentGen != syncGeneration.get()) {
                        LOGGER.debug("Dropping stale weather response (gen {} != current {})", currentGen, syncGeneration.get());
                        return weatherState;
                    }

                    if (!config.isEnabled()) {
                        LOGGER.debug("RealWeather was disabled while API request was in flight. Dropping response.");
                        return weatherState;
                    }

                    if (config.getLatitude() != requestLat || config.getLongitude() != requestLon) {
                        LOGGER.debug("Configuration coordinates changed while API request was in flight. Dropping response.");
                        return weatherState;
                    }

                    if (!snapshot.matchesLocation(requestLat, requestLon)) {
                        LOGGER.warn("Received weather snapshot for different location [lat {}, lon {}], expected [{}, {}]. Dropping response.",
                                snapshot.getLatitude(), snapshot.getLongitude(), requestLat, requestLon);
                        return weatherState;
                    }

                    WeatherCondition desired = WeatherMapper.mapSnapshot(snapshot);
                    Instant fetchTime = Instant.now();
                    weatherState.recordFetched(snapshot, desired, fetchTime);

                    if (server != null) {
                        server.execute(() -> {
                            if (currentGen != syncGeneration.get() || !config.isEnabled() || server.isStopped()) {
                                LOGGER.debug("Skipping weather application on server thread: generation changed or server stopping");
                                return;
                            }
                            try {
                                weatherController.applyWeather(server, desired, config);
                                weatherState.recordApplied(desired, Instant.now());
                            } catch (Exception e) {
                                LOGGER.error("Failed to apply weather on server thread: {}", e.getMessage(), e);
                                weatherState.recordError("Failed to apply weather on server thread: " + e.getMessage());
                            }
                        });
                    } else {
                        // Headless / unit test mode without MinecraftServer dispatch
                        weatherState.recordApplied(desired, fetchTime);
                    }

                    LOGGER.info("Weather synchronized successfully for {} [lat: {}, lon: {}]: condition={}, temp={}°C",
                            config.getLocationName(), requestLat, requestLon, desired, snapshot.getTemperature());
                    return weatherState;
                })
                .exceptionally(throwable -> {
                    if (currentGen != syncGeneration.get()) {
                        LOGGER.debug("Ignoring error from obsolete weather request (gen {} != current {})", currentGen, syncGeneration.get());
                        return weatherState;
                    }

                    String errorMsg = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                    LOGGER.warn("Weather synchronization failed for {}: {}. Preserving current world weather.",
                            config.getLocationName(), errorMsg);
                    weatherState.recordError(errorMsg);

                    // If active state has no valid data for the requested location, try cached data for this location
                    if (!weatherState.matchesLocation(requestLat, requestLon)) {
                        Optional<WeatherSnapshot> cached = weatherProvider.getCachedWeather(requestLat, requestLon);
                        cached.filter(s -> s.matchesLocation(requestLat, requestLon)).ifPresent(cachedSnapshot -> {
                            WeatherCondition desired = WeatherMapper.mapSnapshot(cachedSnapshot);
                            weatherState.updateFromCache(cachedSnapshot, desired);
                            if (server != null) {
                                server.execute(() -> {
                                    if (currentGen != syncGeneration.get() || !config.isEnabled() || server.isStopped()) {
                                        return;
                                    }
                                    try {
                                        weatherController.applyWeather(server, desired, config);
                                        weatherState.recordApplied(desired, Instant.now());
                                    } catch (Exception e) {
                                        LOGGER.error("Failed to apply cached weather on server thread: {}", e.getMessage(), e);
                                        weatherState.recordError("Failed to apply cached weather on server thread: " + e.getMessage());
                                    }
                                });
                            } else {
                                weatherState.recordApplied(desired, Instant.now());
                            }
                            LOGGER.info("Applied stale cached weather for {} during API outage: condition={}",
                                    config.getLocationName(), desired);
                        });
                    }

                    return weatherState;
                })
                .whenComplete((state, err) -> activeSyncGeneration.compareAndSet(currentGen, -1));
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
        long active = activeSyncGeneration.get();
        return active != -1 && active == syncGeneration.get();
    }
}
