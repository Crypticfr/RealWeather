package com.realweather.weather;

import com.realweather.api.WeatherSnapshot;

import java.util.Objects;

public final class WeatherMapper {
    private WeatherMapper() {
    }

    public static WeatherCondition mapWmoCode(int wmoCode) {
        return switch (wmoCode) {
            case 0 -> WeatherCondition.CLEAR;
            case 1, 2 -> WeatherCondition.CLOUDY;
            case 3 -> WeatherCondition.OVERCAST;
            case 45, 48 -> WeatherCondition.FOG;
            case 51, 53, 55 -> WeatherCondition.DRIZZLE;
            case 56, 57 -> WeatherCondition.FREEZING_RAIN;
            case 61, 63, 80, 81 -> WeatherCondition.RAIN;
            case 65, 82 -> WeatherCondition.HEAVY_RAIN;
            case 66, 67 -> WeatherCondition.FREEZING_RAIN;
            case 71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW;
            case 95, 96, 99 -> WeatherCondition.THUNDERSTORM;
            default -> WeatherCondition.CLEAR;
        };
    }

    public static WeatherCondition mapSnapshot(WeatherSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        WeatherCondition condition = mapWmoCode(snapshot.getWeatherCode());

        // Intelligent fallback if code was unknown/unrecognized and mapped to default CLEAR
        if (condition == WeatherCondition.CLEAR && snapshot.getWeatherCode() != 0) {
            if (snapshot.getSnowfall() > 0.0) {
                return WeatherCondition.SNOW;
            }
            if (snapshot.getPrecipitation() > 5.0 || snapshot.getRain() > 5.0) {
                return WeatherCondition.HEAVY_RAIN;
            }
            if (snapshot.getPrecipitation() > 0.0 || snapshot.getRain() > 0.0 || snapshot.getShowers() > 0.0) {
                return WeatherCondition.RAIN;
            }
            if (snapshot.getCloudCover() >= 80.0) {
                return WeatherCondition.OVERCAST;
            }
            if (snapshot.getCloudCover() >= 20.0) {
                return WeatherCondition.CLOUDY;
            }
        }
        return condition;
    }
}
