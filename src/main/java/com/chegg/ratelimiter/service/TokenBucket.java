package com.chegg.ratelimiter.service;

public class TokenBucket {
    private final int capacity;
    private final double refillRatePerSecond;
    private double availableTokens;
    private long lastRefillTimestampMillis;

    public TokenBucket(int capacity, int windowSizeInSeconds) {
        this.capacity = capacity;
        this.refillRatePerSecond = (double) capacity / windowSizeInSeconds;
        this.availableTokens = capacity;
        this.lastRefillTimestampMillis = System.currentTimeMillis();
    }
    public long getLastRefillTimestampMillis() {
        return lastRefillTimestampMillis;
    }

    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1) {
            availableTokens -= 1;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = (now - lastRefillTimestampMillis) / 1000.0;
        double tokensToAdd = elapsedSeconds * refillRatePerSecond;
        availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
        lastRefillTimestampMillis = now;
    }

    public synchronized int getAvailableTokens() {
        refill();
        return (int) availableTokens;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getResetTimestampSeconds() {
        // when will bucket be full again, roughly
        double tokensNeeded = capacity - availableTokens;
        double secondsToFull = tokensNeeded / refillRatePerSecond;
        return (System.currentTimeMillis() / 1000) + (long) Math.ceil(secondsToFull);
    }
}