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
        source.sendSuccess(() -> Component.literal(String.format("§7Location: §f%s §7(%.4f, %.4f)",
                config.getLocationName(), config.getLatitude(), config.getLongitude())), false);

        Optional<WeatherSnapshot> snapshot = state.getLatestSnapshot();
        if (snapshot.isPresent()) {
            WeatherSnapshot s = snapshot.get();
            source.sendSuccess(() -> Component.literal(String.format("§7Real-World: §e%s §7(Code: %d, Temp: %.1f°C, Cloud: %.0f%%, Precip: %.1fmm)",
                    state.getDesiredCondition(), s.getWeatherCode(), s.getTemperature(), s.getCloudCover(), s.getPrecipitation())), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Real-World: §cNo data received yet"), false);
        }

        WeatherCondition currentWorld = manager.getWeatherController().getCurrentWorldCondition(source.getServer());
        source.sendSuccess(() -> Component.literal("§7Minecraft Overworld: §b" + currentWorld), false);

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
        WeatherConfig config = mod.getConfigManager().getConfig();
        config.setEnabled(false);
        mod.getConfigManager().saveConfig();
        mod.getWeatherManager().getWeatherController().restoreVanillaWeather(source.getServer());
        source.sendSuccess(() -> Component.literal("§cRealWeather synchronization disabled. Restored vanilla weather cycle."), true);
        return 1;
    }
}
