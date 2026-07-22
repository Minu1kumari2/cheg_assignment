package com.chegg.ratelimiter.service;

public class TokenBucketManualTest {

    public static void main(String[] args) {
        // Create a bucket: 5 tokens max, refilling over 10 seconds
        TokenBucket bucket = new TokenBucket(5, 10);

        // Try consuming 6 times in a row, immediately
        for (int i = 1; i <= 6; i++) {
            boolean allowed = bucket.tryConsume();
            System.out.println("Request " + i + " allowed? " + allowed);
        }
    }
}
