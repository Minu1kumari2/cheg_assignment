package com.chegg.ratelimiter.config;

import java.util.Map;

public class RateLimitConfig {

    public static final Map<String, int[]> ENDPOINT_LIMITS = Map.of(
        "/api/general", new int[] { 20, 60 },   // limit, windowSizeInSeconds
        "/api/submit",  new int[] { 5, 60 },
        "/api/status",  new int[] { 60, 60 }
    );

    // fallback if a path isn't in the map above
    public static final int DEFAULT_LIMIT = 20;
    public static final int DEFAULT_WINDOW_SECONDS = 60;
}