package com.realweather.config;

import java.util.Objects;

public class WeatherConfig {
    public enum OvercastHandling {
        NONE,
        DOWNFALL,
        RAIN_ONLY
    }

    private boolean enabled = true;
    private String weatherProvider = "open-meteo";
    private String locationName = "London, United Kingdom";
    private double latitude = 51.5074;
    private double longitude = -0.1278;
    private int updateIntervalMinutes = 15;
    private boolean syncRain = true;
    private boolean syncThunder = true;
    private boolean syncSnow = true;
    private OvercastHandling overcastHandling = OvercastHandling.RAIN_ONLY;

    private int apiTimeoutSeconds = 10;
    private int retryIntervalSeconds = 60;
    private int cacheDurationMinutes = 30;
    private boolean debugLogging = false;

    public WeatherConfig() {
    }

    public void validate() {
        if (locationName == null || locationName.isBlank()) {
            locationName = "Default Location";
        }
        if (weatherProvider == null || weatherProvider.isBlank()) {
            weatherProvider = "open-meteo";
        }
        if (latitude < -90.0 || latitude > 90.0) {
            latitude = Math.max(-90.0, Math.min(90.0, latitude));
        }
        if (longitude < -180.0 || longitude > 180.0) {
            longitude = Math.max(-180.0, Math.min(180.0, longitude));
        }
        if (updateIntervalMinutes < 1) {
            updateIntervalMinutes = 15;
        }
        if (apiTimeoutSeconds < 1) {
            apiTimeoutSeconds = 10;
        }
        if (retryIntervalSeconds < 5) {
            retryIntervalSeconds = 60;
        }
        if (cacheDurationMinutes < 1) {
            cacheDurationMinutes = 30;
        }
        if (overcastHandling == null) {
            overcastHandling = OvercastHandling.RAIN_ONLY;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWeatherProvider() {
        return weatherProvider;
    }

    public void setWeatherProvider(String weatherProvider) {
        this.weatherProvider = weatherProvider;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getUpdateIntervalMinutes() {
        return updateIntervalMinutes;
    }

    public void setUpdateIntervalMinutes(int updateIntervalMinutes) {
        this.updateIntervalMinutes = updateIntervalMinutes;
    }

    public boolean isSyncRain() {
        return syncRain;
    }

    public void setSyncRain(boolean syncRain) {
        this.syncRain = syncRain;
    }

    public boolean isSyncThunder() {
        return syncThunder;
    }

    public void setSyncThunder(boolean syncThunder) {
        this.syncThunder = syncThunder;
    }

    public boolean isSyncSnow() {
        return syncSnow;
    }

    public void setSyncSnow(boolean syncSnow) {
        this.syncSnow = syncSnow;
    }

    public OvercastHandling getOvercastHandling() {
        return overcastHandling;
    }

    public void setOvercastHandling(OvercastHandling overcastHandling) {
        this.overcastHandling = Objects.requireNonNullElse(overcastHandling, OvercastHandling.RAIN_ONLY);
    }

    public int getApiTimeoutSeconds() {
        return apiTimeoutSeconds;
    }

    public void setApiTimeoutSeconds(int apiTimeoutSeconds) {
        this.apiTimeoutSeconds = apiTimeoutSeconds;
    }

    public int getRetryIntervalSeconds() {
        return retryIntervalSeconds;
    }

    public void setRetryIntervalSeconds(int retryIntervalSeconds) {
        this.retryIntervalSeconds = retryIntervalSeconds;
    }

    public int getCacheDurationMinutes() {
        return cacheDurationMinutes;
    }

    public void setCacheDurationMinutes(int cacheDurationMinutes) {
        this.cacheDurationMinutes = cacheDurationMinutes;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }
}
