package com.realweather.weather;

public enum WeatherCondition {
    CLEAR,
    CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    THUNDERSTORM,
    SNOW,
    FREEZING_RAIN;

    public boolean isPrecipitating() {
        return this == DRIZZLE || this == RAIN || this == HEAVY_RAIN ||
                this == THUNDERSTORM || this == SNOW || this == FREEZING_RAIN;
    }

    public boolean isThunder() {
        return this == THUNDERSTORM;
    }

    public boolean isSnow() {
        return this == SNOW;
    }
}
