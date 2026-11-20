# RealWeather

**RealWeather** is a lightweight, strictly server-side Fabric mod for Minecraft Java Edition 26.3 (Java 25). It synchronizes the Minecraft Overworld's weather in real-time with live real-world weather retrieved from Open-Meteo, mapping real conditions into Minecraft's three native weather states:

1. **CLEAR**
2. **RAIN**
3. **THUNDERSTORM**

---

## Architecture & Principles

- **100% Server-Sided**: Operates purely on the server. Clients do not need RealWeather installed; vanilla Minecraft client weather synchronization automatically displays weather updates.
- **Zero Client Overhead**: No custom client renderers, no particle injectors, no client mixins, and no client-side dependencies.
- **Daytime Thunderstorm Sleep Restriction**: Prevents players from sleeping during daytime thunderstorms with a descriptive overlay message: *"You cannot sleep during a real-world thunderstorm."* Nighttime sleep is always preserved.
- **Vanilla Mechanics**: Biome temperature and any external seasons mod determine whether precipitation falls as rain or snow according to native Minecraft rules.
- **Overworld Exclusivity**: Weather synchronization applies strictly to the Overworld (`minecraft:overworld`). The Nether and The End remain untouched.
- **Asynchronous & Non-Blocking**: Open-Meteo requests execute asynchronously via Java 25 `HttpClient` without blocking server ticks.
- **Revision-Tracked Stale Prevention**: In-flight requests carry generation revision counters. Responses from superseded requests, coordinate changes, or disabled states are discarded safely.
- **Location-Aware Caching**: Weather snapshots and fallback caches are keyed to geographic coordinates to prevent cross-location data leakage.
- **Vanilla Restoration**: Disabling the mod safely restores the native Minecraft weather cycle via `overworld.resetWeatherCycle()`.

---

## Weather Mapping & Sleep Behaviour

| Time | Real-World Condition | Minecraft Weather | Sleeping Behaviour |
|---|---|---|---|
| **Daytime** | Clear / Clouds / Overcast / Fog | `CLEAR` | Normal vanilla behaviour |
| **Daytime** | Rain / Drizzle / Snow | `RAIN` | Normal vanilla behaviour |
| **Daytime** | Thunderstorm | `THUNDERSTORM` | **Denied**: *"You cannot sleep during a real-world thunderstorm."* |
| **Nighttime** | Clear / Clouds / Overcast / Fog | `CLEAR` | Allowed (vanilla validation) |
| **Nighttime** | Rain / Drizzle / Snow | `RAIN` | Allowed (vanilla validation) |
| **Nighttime** | Thunderstorm | `THUNDERSTORM` | Allowed (vanilla validation) |

> Cloud cover and fog are never treated as precipitation.

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
