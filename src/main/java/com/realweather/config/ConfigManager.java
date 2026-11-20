package com.realweather.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.realweather.RealWeather;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "realweather.json";

    private final Path configPath;
    private volatile WeatherConfig currentConfig;

    public ConfigManager() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        this.currentConfig = new WeatherConfig();
    }

    public WeatherConfig getConfig() {
        return currentConfig;
    }

    public synchronized void setConfig(WeatherConfig newConfig) {
        if (newConfig != null) {
            newConfig.validate();
            this.currentConfig = newConfig;
        }
    }

    public synchronized void loadConfig() {
        if (!Files.exists(configPath)) {
            RealWeather.LOGGER.info("Config file not found at {}, creating default configuration...", configPath);
            this.currentConfig = new WeatherConfig();
            this.currentConfig.validate();
            saveConfig();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            WeatherConfig loaded = GSON.fromJson(reader, WeatherConfig.class);
            if (loaded == null) {
                RealWeather.LOGGER.warn("Config file was empty, creating default configuration.");
                this.currentConfig = new WeatherConfig();
            } else {
                loaded.validate();
                this.currentConfig = loaded;
                RealWeather.LOGGER.info("Loaded configuration successfully from {}.", configPath);
            }
        } catch (Exception e) {
            RealWeather.LOGGER.error("Failed to parse config file: {}. Backing up corrupted file and writing defaults.", e.getMessage());
            backupCorruptedConfig();
            this.currentConfig = new WeatherConfig();
            this.currentConfig.validate();
            saveConfig();
        }
    }

    public synchronized void saveConfig() {
        try {
            if (configPath.getParent() != null && !Files.exists(configPath.getParent())) {
                Files.createDirectories(configPath.getParent());
            }

            Path tempFile = configPath.resolveSibling(CONFIG_FILE_NAME + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(currentConfig, writer);
            }

            Files.move(tempFile, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            RealWeather.LOGGER.info("Saved configuration to {}.", configPath);
        } catch (IOException e) {
            RealWeather.LOGGER.error("Failed to save configuration to {}: {}", configPath, e.getMessage());
        }
    }

    private void backupCorruptedConfig() {
        try {
            if (Files.exists(configPath)) {
                Path backup = configPath.resolveSibling(CONFIG_FILE_NAME + ".corrupted." + Instant.now().toEpochMilli());
                Files.move(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
                RealWeather.LOGGER.warn("Corrupted configuration backed up to {}.", backup);
            }
        } catch (IOException e) {
            RealWeather.LOGGER.error("Failed to backup corrupted config file: {}", e.getMessage());
        }
    }
}
