package com.chegg.ratelimiter.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private static final long STALE_THRESHOLD_MILLIS = 10 * 60 * 1000;

    public boolean isAllowed(String clientId, String endpointPath, int limit, int windowSizeInSeconds) {
        TokenBucket bucket = getOrCreateBucket(clientId, endpointPath, limit, windowSizeInSeconds);
        return bucket.tryConsume();
    }

    public int getRemainingTokens(String clientId, String endpointPath, int limit, int windowSizeInSeconds) {
        TokenBucket bucket = getOrCreateBucket(clientId, endpointPath, limit, windowSizeInSeconds);
        return bucket.getAvailableTokens();
    }

    public long getResetTimestamp(String clientId, String endpointPath, int limit, int windowSizeInSeconds) {
        TokenBucket bucket = getOrCreateBucket(clientId, endpointPath, limit, windowSizeInSeconds);
        return bucket.getResetTimestampSeconds();
    }

    private TokenBucket getOrCreateBucket(String clientId, String endpointPath, int limit, int windowSizeInSeconds) {
        String key = clientId + ":" + endpointPath;
        return buckets.computeIfAbsent(key, k -> new TokenBucket(limit, windowSizeInSeconds));
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry ->
            (now - entry.getValue().getLastRefillTimestampMillis()) > STALE_THRESHOLD_MILLIS
        );
    }
}