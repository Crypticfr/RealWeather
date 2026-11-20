package com.realweather.api;

import java.time.Duration;

public class WeatherRateLimitException extends WeatherApiException {
    public WeatherRateLimitException(String message, Duration retryAfter) {
        super(message, 429, retryAfter, null);
    }
}
