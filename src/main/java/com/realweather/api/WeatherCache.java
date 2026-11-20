package com.realweather.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WeatherCache {
    public record CoordinateKey(long latE4, long lonE4) {
        public static CoordinateKey of(double lat, double lon) {
            return new CoordinateKey(Math.round(lat * 10000.0), Math.round(lon * 10000.0));
        }
    }

    public record Entry(WeatherSnapshot snapshot, Instant cachedAt) {
        public boolean isExpired(Duration maxAge, Instant now) {
            return !now.isBefore(cachedAt.plus(maxAge));
        }
    }

    private final ConcurrentHashMap<CoordinateKey, Entry> cache = new ConcurrentHashMap<>();

    public Optional<WeatherSnapshot> getIfFresh(double latitude, double longitude, Duration maxAge) {
        CoordinateKey key = CoordinateKey.of(latitude, longitude);
        Entry entry = cache.get(key);
        if (entry != null && !entry.isExpired(maxAge, Instant.now())) {
            return Optional.of(entry.snapshot());
        }
        return Optional.empty();
    }

    public Optional<WeatherSnapshot> getAny(double latitude, double longitude) {
        CoordinateKey key = CoordinateKey.of(latitude, longitude);
        Entry entry = cache.get(key);
        return entry != null ? Optional.of(entry.snapshot()) : Optional.empty();
    }

    public void put(double latitude, double longitude, WeatherSnapshot snapshot) {
        if (snapshot != null) {
            CoordinateKey key = CoordinateKey.of(latitude, longitude);
            cache.put(key, new Entry(snapshot, Instant.now()));
        }
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
