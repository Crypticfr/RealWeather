package com.realweather;

import com.realweather.api.OpenMeteoProvider;
import com.realweather.config.ConfigManager;
import com.realweather.server.WeatherCommands;
import com.realweather.server.WeatherController;
import com.realweather.server.WeatherManager;
import com.realweather.server.WeatherScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealWeather implements ModInitializer {
    public static final String MOD_ID = "realweather";
    public static final String MOD_NAME = "RealWeather";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RealWeather instance;

    private final ConfigManager configManager = new ConfigManager();
    private WeatherController weatherController;
    private WeatherManager weatherManager;
    private WeatherScheduler weatherScheduler;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Starting {} mod initialization...", MOD_NAME);

        // Load configuration
        configManager.loadConfig();

        // Initialize core weather services
        this.weatherController = new WeatherController();
        OpenMeteoProvider provider = new OpenMeteoProvider();
        this.weatherManager = new WeatherManager(provider, weatherController);
        this.weatherScheduler = new WeatherScheduler(weatherManager);

        // Register Fabric server lifecycle events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> weatherScheduler.onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> weatherScheduler.onServerStopping(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> weatherScheduler.onServerTick(server));

        // Register server commands
        CommandRegistrationCallback.EVENT.register(WeatherCommands::register);

        var config = configManager.getConfig();
        LOGGER.info("{} initialized. Location: {} [lat: {}, lon: {}], interval: {} min, enabled: {}",
                MOD_NAME,
                config.getLocationName(),
                config.getLatitude(),
                config.getLongitude(),
                config.getUpdateIntervalMinutes(),
                config.isEnabled());
    }

    public static RealWeather getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WeatherController getWeatherController() {
        return weatherController;
    }

    public WeatherManager getWeatherManager() {
        return weatherManager;
    }

    public WeatherScheduler getWeatherScheduler() {
        return weatherScheduler;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
