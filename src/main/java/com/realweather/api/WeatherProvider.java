package com.realweather.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface WeatherProvider {
    String getId();
    String getName();

    CompletableFuture<WeatherSnapshot> getCurrentWeather(double latitude, double longitude);

    Optional<WeatherSnapshot> getCachedWeather(double latitude, double longitude);

    static void validateCoordinates(double latitude, double longitude) {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0. Provided: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0. Provided: " + longitude);
        }
    }
}
