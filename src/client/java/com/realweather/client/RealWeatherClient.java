package com.realweather.client;

import com.realweather.RealWeather;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class RealWeatherClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RealWeather.LOGGER.info("RealWeather client initialized.");
    }
}
