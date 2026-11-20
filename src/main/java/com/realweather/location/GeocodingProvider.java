package com.realweather.location;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GeocodingProvider {
    CompletableFuture<List<LocationData>> search(String query, int maxResults);
}
