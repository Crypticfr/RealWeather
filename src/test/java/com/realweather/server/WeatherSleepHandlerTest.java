package com.realweather.server;

import com.realweather.weather.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSleepHandlerTest {

    @Test
    @DisplayName("Case 1: Daytime + synchronized thunderstorm = sleep denied")
    void testDaytimeThunderstormSleepDenied() {
        boolean deny = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.THUNDERSTORM, true);
        assertTrue(deny, "Sleep must be denied during daytime thunderstorms when RealWeather is enabled");
    }

    @Test
    @DisplayName("Case 2: Daytime + rain = normal vanilla behaviour (allowed by RealWeather)")
    void testDaytimeRainSleepAllowed() {
        boolean deny = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.RAIN, true);
        assertFalse(deny, "Daytime rain must not be denied by RealWeather (proceed to vanilla)");

        boolean denyDrizzle = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.DRIZZLE, true);
        assertFalse(denyDrizzle);

        boolean denyHeavyRain = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.HEAVY_RAIN, true);
        assertFalse(denyHeavyRain);

        boolean denyFreezingRain = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.FREEZING_RAIN, true);
        assertFalse(denyFreezingRain);
    }

    @Test
    @DisplayName("Case 3: Daytime + clear = normal vanilla behaviour (allowed by RealWeather)")
    void testDaytimeClearSleepAllowed() {
        boolean denyClear = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.CLEAR, true);
        assertFalse(denyClear, "Daytime clear weather must not be denied by RealWeather (proceed to vanilla)");

        boolean denyCloudy = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.CLOUDY, true);
        assertFalse(denyCloudy);

        boolean denyOvercast = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.OVERCAST, true);
        assertFalse(denyOvercast);

        boolean denyFog = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.FOG, true);
        assertFalse(denyFog);
    }

    @Test
    @DisplayName("Case 4: Nighttime + thunderstorm = sleep allowed (proceeds through vanilla sleep validation)")
    void testNighttimeThunderstormSleepAllowed() {
        boolean deny = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.THUNDERSTORM, false);
        assertFalse(deny, "Players must always be able to sleep at night during thunderstorms");
    }

    @Test
    @DisplayName("Case 5: Nighttime + rain = sleep allowed")
    void testNighttimeRainSleepAllowed() {
        boolean deny = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.RAIN, false);
        assertFalse(deny, "Players must always be able to sleep at night during rain");
    }

    @Test
    @DisplayName("Case 6: Nighttime + clear = sleep allowed")
    void testNighttimeClearSleepAllowed() {
        boolean deny = WeatherSleepHandler.shouldDenySleep(true, WeatherCondition.CLEAR, false);
        assertFalse(deny, "Players must always be able to sleep at night during clear weather");
    }

    @Test
    @DisplayName("Case 7: RealWeather disabled = normal vanilla behaviour")
    void testDisabledModAllowsSleep() {
        boolean denyDaytimeThunder = WeatherSleepHandler.shouldDenySleep(false, WeatherCondition.THUNDERSTORM, true);
        assertFalse(denyDaytimeThunder, "When RealWeather is disabled, sleep should never be denied by the mod");

        boolean denyNighttimeThunder = WeatherSleepHandler.shouldDenySleep(false, WeatherCondition.THUNDERSTORM, false);
        assertFalse(denyNighttimeThunder);
    }

    @Test
    @DisplayName("Verify daytime clock calculations across Minecraft tick markers")
    void testDaytimeClockCalculations() {
        // Daytime: [0, 12541] and [23460, 24000)
        assertTrue(WeatherSleepHandler.isDaytime(0L), "Tick 0 (dawn) should be daytime");
        assertTrue(WeatherSleepHandler.isDaytime(1000L), "Tick 1000 (morning) should be daytime");
        assertTrue(WeatherSleepHandler.isDaytime(6000L), "Tick 6000 (noon) should be daytime");
        assertTrue(WeatherSleepHandler.isDaytime(12000L), "Tick 12000 (sunset) should be daytime");
        assertTrue(WeatherSleepHandler.isDaytime(12541L), "Tick 12541 (pre-dusk boundary) should be daytime");

        // Nighttime: [12542, 23459]
        assertFalse(WeatherSleepHandler.isDaytime(12542L), "Tick 12542 (dusk/night start) should be nighttime");
        assertFalse(WeatherSleepHandler.isDaytime(13000L), "Tick 13000 (early night) should be nighttime");
        assertFalse(WeatherSleepHandler.isDaytime(18000L), "Tick 18000 (midnight) should be nighttime");
        assertFalse(WeatherSleepHandler.isDaytime(23459L), "Tick 23459 (pre-dawn boundary) should be nighttime");

        // Wrap around dawn
        assertTrue(WeatherSleepHandler.isDaytime(23460L), "Tick 23460 (sunrise) should be daytime");
        assertTrue(WeatherSleepHandler.isDaytime(23999L), "Tick 23999 (dawn) should be daytime");

        // Multi-day rollover (tick count > 24000)
        assertTrue(WeatherSleepHandler.isDaytime(24000L * 10 + 6000L), "Day 10 noon should be daytime");
        assertFalse(WeatherSleepHandler.isDaytime(24000L * 10 + 18000L), "Day 10 midnight should be nighttime");
    }

    @Test
    @DisplayName("Verify denial message matches user requirement exactly")
    void testDenialMessage() {
        assertEquals("You cannot sleep during a real-world thunderstorm.", WeatherSleepHandler.RESTRICTION_MESSAGE);
    }
}
