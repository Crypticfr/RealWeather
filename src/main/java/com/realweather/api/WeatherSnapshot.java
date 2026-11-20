package com.realweather.api;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;

public final class WeatherSnapshot {
    private final int weatherCode;
    private final double temperature;
    private final Double apparentTemperature;
    private final Double relativeHumidity;
    private final double precipitation;
    private final double rain;
    private final double showers;
    private final double snowfall;
    private final double cloudCover;
    private final Double windSpeed;
    private final Double windDirection;
    private final Instant observationTime;
    private final String timezone;
    private final Instant fetchTime;

    public WeatherSnapshot(
            int weatherCode,
            double temperature,
            Double apparentTemperature,
            Double relativeHumidity,
            double precipitation,
            double rain,
            double showers,
            double snowfall,
            double cloudCover,
            Double windSpeed,
            Double windDirection,
            Instant observationTime,
            String timezone,
            Instant fetchTime
    ) {
        if (weatherCode < 0) {
            throw new IllegalArgumentException("Weather code cannot be negative: " + weatherCode);
        }
        if (precipitation < 0.0) {
            throw new IllegalArgumentException("Precipitation cannot be negative: " + precipitation);
        }
        if (rain < 0.0) {
            throw new IllegalArgumentException("Rain cannot be negative: " + rain);
        }
        if (showers < 0.0) {
            throw new IllegalArgumentException("Showers cannot be negative: " + showers);
        }
        if (snowfall < 0.0) {
            throw new IllegalArgumentException("Snowfall cannot be negative: " + snowfall);
        }
        this.weatherCode = weatherCode;
        this.temperature = temperature;
        this.apparentTemperature = apparentTemperature;
        this.relativeHumidity = relativeHumidity;
        this.precipitation = precipitation;
        this.rain = rain;
        this.showers = showers;
        this.snowfall = snowfall;
        this.cloudCover = Math.max(0.0, Math.min(100.0, cloudCover));
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.observationTime = Objects.requireNonNull(observationTime, "observationTime cannot be null");
        this.timezone = Objects.requireNonNull(timezone, "timezone cannot be null");
        this.fetchTime = Objects.requireNonNull(fetchTime, "fetchTime cannot be null");
    }

    public int getWeatherCode() {
        return weatherCode;
    }

    public double getTemperature() {
        return temperature;
    }

    public OptionalDouble getApparentTemperature() {
        return apparentTemperature != null ? OptionalDouble.of(apparentTemperature) : OptionalDouble.empty();
    }

    public OptionalDouble getRelativeHumidity() {
        return relativeHumidity != null ? OptionalDouble.of(relativeHumidity) : OptionalDouble.empty();
    }

    public double getPrecipitation() {
        return precipitation;
    }

    public double getRain() {
        return rain;
    }

    public double getShowers() {
        return showers;
    }

    public double getSnowfall() {
        return snowfall;
    }

    public double getCloudCover() {
        return cloudCover;
    }

    public OptionalDouble getWindSpeed() {
        return windSpeed != null ? OptionalDouble.of(windSpeed) : OptionalDouble.empty();
    }

    public OptionalDouble getWindDirection() {
        return windDirection != null ? OptionalDouble.of(windDirection) : OptionalDouble.empty();
    }

    public Instant getObservationTime() {
        return observationTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getFetchTime() {
        return fetchTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeatherSnapshot that)) return false;
        return weatherCode == that.weatherCode &&
                Double.compare(that.temperature, temperature) == 0 &&
                Double.compare(that.precipitation, precipitation) == 0 &&
                Double.compare(that.rain, rain) == 0 &&
                Double.compare(that.showers, showers) == 0 &&
                Double.compare(that.snowfall, snowfall) == 0 &&
                Double.compare(that.cloudCover, cloudCover) == 0 &&
                Objects.equals(apparentTemperature, that.apparentTemperature) &&
                Objects.equals(relativeHumidity, that.relativeHumidity) &&
                Objects.equals(windSpeed, that.windSpeed) &&
                Objects.equals(windDirection, that.windDirection) &&
                Objects.equals(observationTime, that.observationTime) &&
                Objects.equals(timezone, that.timezone) &&
                Objects.equals(fetchTime, that.fetchTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(weatherCode, temperature, apparentTemperature, relativeHumidity,
                precipitation, rain, showers, snowfall, cloudCover, windSpeed, windDirection,
                observationTime, timezone, fetchTime);
    }

    @Override
    public String toString() {
        return "WeatherSnapshot{" +
                "weatherCode=" + weatherCode +
                ", temperature=" + temperature + "°C" +
                ", apparent=" + (apparentTemperature != null ? apparentTemperature + "°C" : "N/A") +
                ", humidity=" + (relativeHumidity != null ? relativeHumidity + "%" : "N/A") +
                ", precip=" + precipitation + "mm" +
                ", rain=" + rain + "mm" +
                ", showers=" + showers + "mm" +
                ", snowfall=" + snowfall + "cm" +
                ", cloudCover=" + cloudCover + "%" +
                ", windSpeed=" + (windSpeed != null ? windSpeed + "km/h" : "N/A") +
                ", windDirection=" + (windDirection != null ? windDirection + "°" : "N/A") +
                ", observationTime=" + observationTime +
                ", timezone='" + timezone + '\'' +
                ", fetchTime=" + fetchTime +
                '}';
    }
}
