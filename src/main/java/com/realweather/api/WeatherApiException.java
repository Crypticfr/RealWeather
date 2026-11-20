package com.realweather.api;

import java.time.Duration;
import java.util.Optional;

public class WeatherApiException extends Exception {
    private final int statusCode;
    private final Duration retryAfter;

    public WeatherApiException(String message) {
        this(message, 0, null, null);
    }

    public WeatherApiException(String message, Throwable cause) {
        this(message, 0, null, cause);
    }

    public WeatherApiException(String message, int statusCode) {
        this(message, statusCode, null, null);
    }

    public WeatherApiException(String message, int statusCode, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Optional<Duration> getRetryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
}
