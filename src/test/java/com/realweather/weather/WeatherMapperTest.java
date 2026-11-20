package com.realweather.weather;

import com.realweather.api.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WeatherMapperTest {

    @ParameterizedTest(name = "WMO code {0} should map to {1}")
    @CsvSource({
            "0, CLEAR",
            "1, CLOUDY",
            "2, CLOUDY",
            "3, OVERCAST",
            "45, FOG",
            "48, FOG",
            "51, DRIZZLE",
            "53, DRIZZLE",
            "55, DRIZZLE",
            "56, FREEZING_RAIN",
            "57, FREEZING_RAIN",
            "61, RAIN",
            "63, RAIN",
            "65, HEAVY_RAIN",
            "66, FREEZING_RAIN",
            "67, FREEZING_RAIN",
            "71, SNOW",
            "73, SNOW",
            "75, SNOW",
            "77, SNOW",
            "80, RAIN",
            "81, RAIN",
            "82, HEAVY_RAIN",
            "85, SNOW",
            "86, SNOW",
            "95, THUNDERSTORM",
            "96, THUNDERSTORM",
            "99, THUNDERSTORM"
    })
    void testDirectWmoCodeMapping(int wmoCode, WeatherCondition expected) {
        assertEquals(expected, WeatherMapper.mapWmoCode(wmoCode));
    }

    @Test
    @DisplayName("Verify condition helper flags (isPrecipitating, isThunder, isSnow)")
    void testConditionHelperMethods() {
        assertFalse(WeatherCondition.CLEAR.isPrecipitating());
        assertFalse(WeatherCondition.CLOUDY.isPrecipitating());
        assertFalse(WeatherCondition.OVERCAST.isPrecipitating());
        assertFalse(WeatherCondition.FOG.isPrecipitating());

        assertTrue(WeatherCondition.DRIZZLE.isPrecipitating());
        assertTrue(WeatherCondition.RAIN.isPrecipitating());
        assertTrue(WeatherCondition.HEAVY_RAIN.isPrecipitating());
        assertTrue(WeatherCondition.THUNDERSTORM.isPrecipitating());
        assertTrue(WeatherCondition.SNOW.isPrecipitating());
        assertTrue(WeatherCondition.FREEZING_RAIN.isPrecipitating());

        assertTrue(WeatherCondition.THUNDERSTORM.isThunder());
        assertFalse(WeatherCondition.RAIN.isThunder());

        assertTrue(WeatherCondition.SNOW.isSnow());
        assertFalse(WeatherCondition.FREEZING_RAIN.isSnow());
    }

    @Test
    @DisplayName("Verify snapshot fallback mapping when WMO code is unknown")
    void testSnapshotFallbackMapping() {
        Instant now = Instant.now();

        // Unknown code with snow
        WeatherSnapshot snowFallback = new WeatherSnapshot(
                999, 0.0, null, null, 2.0, 0.0, 0.0, 3.0, 100.0, null, null, now, "UTC", now
        );
        assertEquals(WeatherCondition.SNOW, WeatherMapper.mapSnapshot(snowFallback));

        // Unknown code with heavy rain
        WeatherSnapshot heavyRainFallback = new WeatherSnapshot(
                999, 15.0, null, null, 12.0, 10.0, 2.0, 0.0, 95.0, null, null, now, "UTC", now
        );
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherMapper.mapSnapshot(heavyRainFallback));

        // Unknown code with moderate rain
        WeatherSnapshot rainFallback = new WeatherSnapshot(
                999, 12.0, null, null, 1.5, 1.5, 0.0, 0.0, 90.0, null, null, now, "UTC", now
        );
        assertEquals(WeatherCondition.RAIN, WeatherMapper.mapSnapshot(rainFallback));

        // Unknown code with heavy cloud cover (no rain)
        WeatherSnapshot overcastFallback = new WeatherSnapshot(
                999, 18.0, null, null, 0.0, 0.0, 0.0, 0.0, 85.0, null, null, now, "UTC", now
        );
        assertEquals(WeatherCondition.OVERCAST, WeatherMapper.mapSnapshot(overcastFallback));

        // Unknown code with partial cloud cover
        WeatherSnapshot cloudyFallback = new WeatherSnapshot(
                999, 20.0, null, null, 0.0, 0.0, 0.0, 0.0, 45.0, null, null, now, "UTC", now
        );
        assertEquals(WeatherCondition.CLOUDY, WeatherMapper.mapSnapshot(cloudyFallback));

        // Unknown code with clear skies
        WeatherSnapshot clearFallback = new WeatherSnapshot(
                999, 22.0, null, null, 0.0, 0.0, 0.0, 0.0, 5.0, null, null, now, "UTC", now
        );
        assertEquals(WeatherCondition.CLEAR, WeatherMapper.mapSnapshot(clearFallback));
    }
}
