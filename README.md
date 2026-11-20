# RealWeather

RealWeather is a Fabric mod for Minecraft Java Edition 26.3 that synchronizes the Overworld's weather with real-world weather conditions of any configured geographical location using Open-Meteo.

## Features
- **Server Authoritative**: Runs on dedicated servers without requiring players to install client mods.
- **Real-World Synchronization**: Accurately fetches live weather data (temperature, cloud cover, precipitation, storms).
- **Overworld Exclusive**: Does not affect the Nether or The End.
- **Biome Preservation**: Preserves native Minecraft biome rules (snow in cold biomes, rain in temperate biomes).

## Building
Run with the Gradle wrapper:
```bash
./gradlew build
```

## License
MIT License
