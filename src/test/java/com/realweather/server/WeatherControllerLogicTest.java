package com.realweather.server;

import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherControllerLogicTest {

    @Test
    @DisplayName("Verify dimension filtering: Overworld only")
    void testDimensionFiltering() {
        var dimRegistry = net.minecraft.resources.ResourceKey.createRegistryKey(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "dimension")
        );
        var overworldKey = net.minecraft.resources.ResourceKey.create(dimRegistry,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "overworld"));
        var netherKey = net.minecraft.resources.ResourceKey.create(dimRegistry,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "the_nether"));
        var endKey = net.minecraft.resources.ResourceKey.create(dimRegistry,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "the_end"));

        assertTrue(WeatherController.isOverworld(overworldKey));
        assertFalse(WeatherController.isOverworld(netherKey));
        assertFalse(WeatherController.isOverworld(endKey));
        assertFalse(WeatherController.isOverworld(null));
    }

    @Test
    @DisplayName("Verify target weather calculations for CLEAR, CLOUDY, OVERCAST and FOG map to CLEAR")
    void testNonPrecipitatingConditionsMapToClear() {
        WeatherConfig config = new WeatherConfig();
        int duration = 24000;

        // Clear
        WeatherController.TargetWeather clearTarget = WeatherController.calculateTargetWeather(WeatherCondition.CLEAR, config, duration);
        assertEquals(24000, clearTarget.clearTime());
        assertEquals(0, clearTarget.weatherTime());
        assertFalse(clearTarget.raining());
        assertFalse(clearTarget.thundering());

        // Cloudy (never precipitation)
        WeatherController.TargetWeather cloudyTarget = WeatherController.calculateTargetWeather(WeatherCondition.CLOUDY, config, duration);
        assertFalse(cloudyTarget.raining());
        assertFalse(cloudyTarget.thundering());
        assertEquals(24000, cloudyTarget.clearTime());

        // Overcast (cloud cover is never precipitation)
        WeatherController.TargetWeather overcastTarget = WeatherController.calculateTargetWeather(WeatherCondition.OVERCAST, config, duration);
        assertFalse(overcastTarget.raining());
        assertFalse(overcastTarget.thundering());
        assertEquals(24000, overcastTarget.clearTime());

        // Fog (fog is never precipitation)
        WeatherController.TargetWeather fogTarget = WeatherController.calculateTargetWeather(WeatherCondition.FOG, config, duration);
        assertFalse(fogTarget.raining());
        assertFalse(fogTarget.thundering());
        assertEquals(24000, fogTarget.clearTime());
    }

    @Test
    @DisplayName("Verify target weather calculations for RAIN, DRIZZLE, HEAVY_RAIN and FREEZING_RAIN map to RAIN")
    void testRainConditions() {
        WeatherConfig config = new WeatherConfig();
        int duration = 24000;

        WeatherController.TargetWeather rainTarget = WeatherController.calculateTargetWeather(WeatherCondition.RAIN, config, duration);
        assertEquals(0, rainTarget.clearTime());
        assertEquals(24000, rainTarget.weatherTime());
        assertTrue(rainTarget.raining());
        assertFalse(rainTarget.thundering());

        WeatherController.TargetWeather drizzleTarget = WeatherController.calculateTargetWeather(WeatherCondition.DRIZZLE, config, duration);
        assertTrue(drizzleTarget.raining());
        assertFalse(drizzleTarget.thundering());

        WeatherController.TargetWeather heavyRainTarget = WeatherController.calculateTargetWeather(WeatherCondition.HEAVY_RAIN, config, duration);
        assertTrue(heavyRainTarget.raining());
        assertFalse(heavyRainTarget.thundering());

        WeatherController.TargetWeather freezingRainTarget = WeatherController.calculateTargetWeather(WeatherCondition.FREEZING_RAIN, config, duration);
        assertTrue(freezingRainTarget.raining());
        assertFalse(freezingRainTarget.thundering());

        // When rain sync is disabled
        config.setSyncRain(false);
        WeatherController.TargetWeather noRainTarget = WeatherController.calculateTargetWeather(WeatherCondition.RAIN, config, duration);
        assertFalse(noRainTarget.raining());
        assertEquals(24000, noRainTarget.clearTime());
    }

    @Test
    @DisplayName("Verify target weather calculations for THUNDERSTORM map to THUNDERSTORM")
    void testThunderstormConditions() {
        WeatherConfig config = new WeatherConfig();
        int duration = 24000;

        WeatherController.TargetWeather thunderTarget = WeatherController.calculateTargetWeather(WeatherCondition.THUNDERSTORM, config, duration);
        assertTrue(thunderTarget.raining());
        assertTrue(thunderTarget.thundering());
        assertEquals(24000, thunderTarget.weatherTime());

        // When thunder sync is disabled but rain sync is enabled, falls back to rain
        config.setSyncThunder(false);
        WeatherController.TargetWeather fallbackTarget = WeatherController.calculateTargetWeather(WeatherCondition.THUNDERSTORM, config, duration);
        assertTrue(fallbackTarget.raining());
        assertFalse(fallbackTarget.thundering());

        // When both thunder and rain sync are disabled
        config.setSyncRain(false);
        WeatherController.TargetWeather disabledTarget = WeatherController.calculateTargetWeather(WeatherCondition.THUNDERSTORM, config, duration);
        assertFalse(disabledTarget.raining());
        assertFalse(disabledTarget.thundering());
    }

    @Test
    @DisplayName("Verify target weather calculations for SNOW map to vanilla RAIN state")
    void testSnowConditions() {
        WeatherConfig config = new WeatherConfig();
        int duration = 24000;

        // In vanilla Minecraft, snowfall occurs during precipitation in cold biomes
        WeatherController.TargetWeather snowTarget = WeatherController.calculateTargetWeather(WeatherCondition.SNOW, config, duration);
        assertTrue(snowTarget.raining());
        assertFalse(snowTarget.thundering());

        // When snow sync is disabled
        config.setSyncSnow(false);
        WeatherController.TargetWeather noSnowTarget = WeatherController.calculateTargetWeather(WeatherCondition.SNOW, config, duration);
        assertFalse(noSnowTarget.raining());
    }

    @Test
    @DisplayName("Verify disabled configuration returns clear weather")
    void testDisabledConfiguration() {
        WeatherConfig config = new WeatherConfig();
        config.setEnabled(false);
        int duration = 24000;

        WeatherController.TargetWeather target = WeatherController.calculateTargetWeather(WeatherCondition.THUNDERSTORM, config, duration);
        assertFalse(target.raining());
        assertFalse(target.thundering());
        assertEquals(24000, target.clearTime());
    }
}
