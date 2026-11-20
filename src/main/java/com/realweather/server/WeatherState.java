package com.realweather.server;

import com.realweather.api.WeatherSnapshot;
import com.realweather.weather.WeatherCondition;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class WeatherState {
    public enum SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR
    }

    private volatile WeatherSnapshot latestSnapshot;
    private volatile WeatherCondition currentAppliedCondition = WeatherCondition.CLEAR;
    private volatile WeatherCondition desiredCondition = WeatherCondition.CLEAR;
    private volatile Instant lastSyncTimestamp;
    private volatile SyncStatus syncStatus = SyncStatus.IDLE;
    private volatile String lastError;

    public WeatherState() {
    }

    public synchronized void updateSuccess(WeatherSnapshot snapshot, WeatherCondition desired, Instant syncTime) {
        this.latestSnapshot = Objects.requireNonNull(snapshot, "snapshot cannot be null");
        this.desiredCondition = Objects.requireNonNull(desired, "desired condition cannot be null");
        this.lastSyncTimestamp = Objects.requireNonNull(syncTime, "syncTime cannot be null");
        this.syncStatus = SyncStatus.SUCCESS;
        this.lastError = null;
    }

    public synchronized void updateFromCache(WeatherSnapshot snapshot, WeatherCondition desired) {
        this.latestSnapshot = Objects.requireNonNull(snapshot, "snapshot cannot be null");
        this.desiredCondition = Objects.requireNonNull(desired, "desired condition cannot be null");
        this.lastSyncTimestamp = snapshot.getFetchTime();
    }

    public synchronized void recordApplied(WeatherCondition applied) {
        this.currentAppliedCondition = Objects.requireNonNull(applied, "applied condition cannot be null");
    }

    public synchronized void recordError(String errorMessage) {
        this.syncStatus = SyncStatus.ERROR;
        this.lastError = errorMessage;
    }

    public synchronized void setSyncing() {
        this.syncStatus = SyncStatus.SYNCING;
    }

    public boolean isStale(Duration maxAge, Instant now) {
        if (lastSyncTimestamp == null) {
            return true;
        }
        return lastSyncTimestamp.plus(maxAge).isBefore(now);
    }

    public Optional<WeatherSnapshot> getLatestSnapshot() {
        return Optional.ofNullable(latestSnapshot);
    }

    public WeatherCondition getCurrentAppliedCondition() {
        return currentAppliedCondition;
    }

    public WeatherCondition getDesiredCondition() {
        return desiredCondition;
    }

    public Optional<Instant> getLastSyncTimestamp() {
        return Optional.ofNullable(lastSyncTimestamp);
    }

    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }
}
