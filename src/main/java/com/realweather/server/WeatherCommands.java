package com.realweather.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.realweather.RealWeather;
import com.realweather.api.WeatherSnapshot;
import com.realweather.config.WeatherConfig;
import com.realweather.weather.WeatherCondition;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public final class WeatherCommands {
    private WeatherCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            Commands.CommandSelection commandSelection
    ) {
        dispatcher.register(
                Commands.literal("realweather")
                        .then(Commands.literal("status")
                                .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                                .executes(WeatherCommands::executeStatus))
                        .then(Commands.literal("refresh")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(WeatherCommands::executeRefresh))
                        .then(Commands.literal("reload")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(WeatherCommands::executeReload))
                        .then(Commands.literal("enable")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(WeatherCommands::executeEnable))
                        .then(Commands.literal("disable")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(WeatherCommands::executeDisable))
        );
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RealWeather mod = RealWeather.getInstance();
        WeatherConfig config = mod.getConfigManager().getConfig();
        WeatherManager manager = mod.getWeatherManager();
        WeatherState state = manager.getWeatherState();

        source.sendSuccess(() -> Component.literal("§6=== RealWeather Status ==="), false);
        source.sendSuccess(() -> Component.literal("§7Status: " + (config.isEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "§7Location: §f%s §7(lat: %.4f, lon: %.4f)",
                config.getLocationName(), config.getLatitude(), config.getLongitude())), false);

        Optional<WeatherSnapshot> snapshotOpt = state.getLatestSnapshot();
        if (snapshotOpt.isPresent() && snapshotOpt.get().matchesLocation(config.getLatitude(), config.getLongitude())) {
            WeatherSnapshot s = snapshotOpt.get();
            WeatherCondition condition = state.getDesiredCondition();
            String windStr = s.getWindSpeed().isPresent()
                    ? String.format(Locale.ROOT, "%.1f km/h", s.getWindSpeed().getAsDouble())
                    : "N/A";
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "§7Real-World Weather: §e%s §7(WMO Code: %d, Temp: %.1f°C, Clouds: %.0f%%, Precip: %.1fmm, Wind: %s)",
                    condition, s.getWeatherCode(), s.getTemperature(), s.getCloudCover(), s.getPrecipitation(), windStr)), false);
        } else if (snapshotOpt.isPresent()) {
            source.sendSuccess(() -> Component.literal("§7Real-World Weather: §eLocation changed. Pending refresh for current coordinates..."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Real-World Weather: §cNo data received yet"), false);
        }

        WeatherCondition currentWorld = manager.getWeatherController().getCurrentWorldCondition(source.getServer());
        String mcPrecipitationDesc;
        if (currentWorld.isThunder()) {
            mcPrecipitationDesc = "§cTHUNDERSTORM";
        } else if (currentWorld.isPrecipitating()) {
            mcPrecipitationDesc = "§9RAIN";
        } else {
            mcPrecipitationDesc = "§aCLEAR";
        }
        source.sendSuccess(() -> Component.literal("§7Minecraft Weather State: " + mcPrecipitationDesc), false);

        state.getLastSyncTimestamp().ifPresentOrElse(
                time -> {
                    boolean stale = state.isStale(Duration.ofMinutes(config.getUpdateIntervalMinutes() * 2L), Instant.now());
                    source.sendSuccess(() -> Component.literal("§7Last Sync: §f" + time + (stale ? " §c(Stale)" : " §a(Fresh)")), false);
                },
                () -> source.sendSuccess(() -> Component.literal("§7Last Sync: §eNever"), false)
        );

        state.getLastError().ifPresent(err ->
                source.sendSuccess(() -> Component.literal("§cLast Error: " + err), false));

        return 1;
    }

    private static int executeRefresh(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RealWeather mod = RealWeather.getInstance();
        source.sendSuccess(() -> Component.literal("§eTriggering immediate weather refresh..."), true);
        mod.getWeatherScheduler().triggerSync(true);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RealWeather mod = RealWeather.getInstance();
        mod.getWeatherManager().invalidateInFlight();
        mod.getConfigManager().loadConfig();
        source.sendSuccess(() -> Component.literal("§aRealWeather configuration reloaded."), true);
        mod.getWeatherScheduler().triggerSync(true);
        return 1;
    }

    private static int executeEnable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RealWeather mod = RealWeather.getInstance();
        WeatherConfig config = mod.getConfigManager().getConfig();
        config.setEnabled(true);
        mod.getConfigManager().saveConfig();
        source.sendSuccess(() -> Component.literal("§aRealWeather synchronization enabled."), true);
        mod.getWeatherScheduler().triggerSync(true);
        return 1;
    }

    private static int executeDisable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RealWeather mod = RealWeather.getInstance();
        mod.getWeatherManager().invalidateInFlight();
        WeatherConfig config = mod.getConfigManager().getConfig();
        config.setEnabled(false);
        mod.getConfigManager().saveConfig();
        mod.getWeatherManager().getWeatherController().restoreVanillaWeather(source.getServer());
        source.sendSuccess(() -> Component.literal("§cRealWeather synchronization disabled. Restored vanilla weather cycle."), true);
        return 1;
    }
}
