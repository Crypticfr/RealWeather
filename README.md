# RealWeather

**RealWeather** is a lightweight, strictly server-side Fabric mod for Minecraft Java Edition 26.3 (Java 25). It synchronizes the Minecraft Overworld's weather in real-time with live real-world weather retrieved from Open-Meteo, mapping real conditions into Minecraft's three native weather states:

1. **CLEAR**
2. **RAIN**
3. **THUNDERSTORM**

---

## Architecture & Principles

- **100% Server-Sided & Mixin-Free**: Operates purely on the server without any bytecode injection or mixins. Seamlessly compatible with optimization mods (such as Lithium or FerriteCore) and custom server distributions. Clients do not need RealWeather installed; native Minecraft client weather synchronization automatically displays weather updates.
- **Zero Client Overhead**: No custom client renderers, no particle injectors, and no client-side dependencies.
- **Periodic Weather Enforcement**: Evaluates the Overworld's actual weather against the active RealWeather target once every 20 server ticks (~1 second). If a mismatch occurs (e.g., from `/weather`, players sleeping through the night, natural vanilla transitions, or other server modifications), RealWeather cleanly detects the divergence and restores the target weather without per-tick CPU overhead.
- **Unrestricted Sleeping**: Players can sleep according to standard vanilla Minecraft mechanics. When sleeping resets the weather cycle, periodic enforcement restores the synchronized real-world weather within 1 second.
- **Vanilla Mechanics**: Biome temperature and any external seasons mod determine whether precipitation falls as rain or snow according to native Minecraft rules.
- **Overworld Exclusivity**: Weather synchronization applies strictly to the Overworld (`minecraft:overworld`). The Nether and The End remain untouched.
- **Asynchronous & Non-Blocking**: Open-Meteo requests execute asynchronously via Java 25 `HttpClient` without blocking server ticks.
- **Revision-Tracked Stale Prevention**: In-flight requests carry generation revision counters. Responses from superseded requests, coordinate changes, or disabled states are discarded safely.
- **Location-Aware Caching**: Weather snapshots and fallback caches are keyed to geographic coordinates to prevent cross-location data leakage.
- **Vanilla Restoration**: Disabling the mod safely restores the native Minecraft weather cycle via `overworld.resetWeatherCycle()`.

---

## Weather Mapping & Periodic Enforcement

### Weather Condition Mapping

| Real-World Condition (Open-Meteo) | Minecraft Weather State | In-Game Behaviour |
|---|---|---|
| Clear, mainly clear, partly cloudy, cloudy, overcast, fog | `CLEAR` | Clear skies. Cloud cover and fog are never treated as precipitation. |
| Drizzle, rain, heavy rain, snowfall, freezing rain | `RAIN` | Rain in temperate biomes; snowfall in cold/high-altitude biomes. |
| Thunderstorms (slight, moderate, severe) | `THUNDERSTORM` | Thunderstorm with lightning. Snowfall in cold biomes. |

### Periodic Enforcement Behaviour

RealWeather maintains control over the Overworld weather through a lightweight periodic check:

- **20-Tick Frequency (1 Hz)**: The enforcement check runs once every 20 Overworld server ticks rather than every tick, reducing coordinator evaluations by 95% while keeping enforcement responsive.
- **Divergence Detection**: Compares the Overworld's actual `isRaining()` and `isThundering()` state with the target RealWeather condition.
- **Non-Invasive Corrections**:
  - **Manual Commands**: Vanilla commands like `/weather clear`, `/weather rain`, or `/weather thunder` are permitted to execute natively without mixin cancellations; RealWeather restores the real-world condition on the next periodic cycle.
  - **Sleeping**: Players can sleep through storms and nighttime without restriction. When morning arrives and clears the storm, RealWeather restores the synchronized weather state within 20 ticks (~1 second).
  - **Vanilla Inactivity**: When RealWeather is disabled via `/realweather disable` or `"enabled": false`, enforcement halts immediately and native Minecraft weather cycles run unhindered.

---

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/realweather status` | All Players (Level 0) | Displays mod status, configured location, real-world weather, Minecraft weather state, and sync timestamps. |
| `/realweather refresh` | Game Masters (Level 2) | Forces an immediate, cache-bypassing sync with Open-Meteo. |
| `/realweather reload` | Game Masters (Level 2) | Reloads `config/realweather.json` from disk, cancels in-flight requests, and initiates a sync. |
| `/realweather enable` | Game Masters (Level 2) | Enables weather synchronization and syncs immediately. |
| `/realweather disable` | Game Masters (Level 2) | Disables weather synchronization and restores the native Minecraft weather cycle. |

---

## Configuration (`config/realweather.json`)

```json
{
  "enabled": true,
  "weatherProvider": "open-meteo",
  "locationName": "London, United Kingdom",
  "latitude": 51.5074,
  "longitude": -0.1278,
  "updateIntervalMinutes": 15,
  "syncRain": true,
  "syncThunder": true,
  "syncSnow": true,
  "apiTimeoutSeconds": 10,
  "retryIntervalSeconds": 60,
  "cacheDurationMinutes": 30,
  "debugLogging": false
}
```

---

## Building from Source

Requires Java 25 JDK.

```bash
# Build the mod JAR
./gradlew build

# Run automated tests
./gradlew test

# Launch dedicated server
./gradlew runServer --args="nogui"
```

Output JAR is generated in `build/libs/`.

---

## License

Licensed under the [MIT License](LICENSE).
