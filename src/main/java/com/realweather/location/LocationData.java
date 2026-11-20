package com.realweather.location;

import java.util.Objects;

public record LocationData(
        String name,
        String country,
        String countryCode,
        String administrativeRegion,
        double latitude,
        double longitude,
        String timezone
) {
    public LocationData {
        Objects.requireNonNull(name, "Location name cannot be null");
    }

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(name);
        if (administrativeRegion != null && !administrativeRegion.isBlank()) {
            sb.append(", ").append(administrativeRegion);
        }
        if (country != null && !country.isBlank()) {
            sb.append(", ").append(country);
        }
        return sb.toString();
    }
}
