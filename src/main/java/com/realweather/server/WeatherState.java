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
        FETCHING,
        FETCHED,
        SUCCESS,
        ERROR
    }

    private volatile WeatherSnapshot latestSnapshot;
    private volatile WeatherCondition currentAppliedCondition = WeatherCondition.CLEAR;
    private volatile WeatherCondition desiredCondition = WeatherCondition.CLEAR;
    private volatile Instant lastFetchTimestamp;
    private volatile Instant lastApplyTimestamp;
    private volatile SyncStatus syncStatus = SyncStatus.IDLE;
    private volatile String lastError;

    public WeatherState() {
    }

    public synchronized void recordFetching() {
        this.syncStatus = SyncStatus.FETCHING;
    }

    public synchronized void recordFetched(WeatherSnapshot snapshot, WeatherCondition desired, Instant fetchTime) {
        this.latestSnapshot = Objects.requireNonNull(snapshot, "snapshot cannot be null");
        this.desiredCondition = Objects.requireNonNull(desired, "desired condition cannot be null");
        this.lastFetchTimestamp = Objects.requireNonNull(fetchTime, "fetchTime cannot be null");
        this.syncStatus = SyncStatus.FETCHED;
        this.lastError = null;
    }

    public synchronized void recordApplied(WeatherCondition applied, Instant applyTime) {
        this.currentAppliedCondition = Objects.requireNonNull(applied, "applied condition cannot be null");
        this.lastApplyTimestamp = Objects.requireNonNull(applyTime, "applyTime cannot be null");
        if (this.syncStatus != SyncStatus.ERROR) {
            this.syncStatus = SyncStatus.SUCCESS;
        }
    }

    public synchronized void updateSuccess(WeatherSnapshot snapshot, WeatherCondition desired, Instant syncTime) {
        recordFetched(snapshot, desired, syncTime);
        recordApplied(desired, syncTime);
    }

    public synchronized void updateFromCache(WeatherSnapshot snapshot, WeatherCondition desired) {
        this.latestSnapshot = Objects.requireNonNull(snapshot, "snapshot cannot be null");
        this.desiredCondition = Objects.requireNonNull(desired, "desired condition cannot be null");
        this.lastFetchTimestamp = snapshot.getFetchTime();
    }

    public synchronized void recordError(String errorMessage) {
        this.syncStatus = SyncStatus.ERROR;
        this.lastError = errorMessage;
    }

    public boolean hasValidAppliedWeather() {
        return latestSnapshot != null && lastApplyTimestamp != null;
    }

    public boolean matchesLocation(double lat, double lon) {
        return latestSnapshot != null && latestSnapshot.matchesLocation(lat, lon);
    }

    public Optional<WeatherSnapshot> getLatestSnapshotForLocation(double lat, double lon) {
        if (latestSnapshot != null && latestSnapshot.matchesLocation(lat, lon)) {
            return Optional.of(latestSnapshot);
        }
        return Optional.empty();
    }

    public boolean isStale(Duration maxAge, Instant now) {
        Instant ref = lastApplyTimestamp != null ? lastApplyTimestamp : lastFetchTimestamp;
        if (ref == null) {
            return true;
        }
        return ref.plus(maxAge).isBefore(now);
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
        return Optional.ofNullable(lastApplyTimestamp != null ? lastApplyTimestamp : lastFetchTimestamp);
    }

    public Optional<Instant> getLastFetchTimestamp() {
        return Optional.ofNullable(lastFetchTimestamp);
    }

    public Optional<Instant> getLastApplyTimestamp() {
        return Optional.ofNullable(lastApplyTimestamp);
    }

    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public Optional<String> getLastError() {
        return Optional.ofNullable(lastError);
    }
}
