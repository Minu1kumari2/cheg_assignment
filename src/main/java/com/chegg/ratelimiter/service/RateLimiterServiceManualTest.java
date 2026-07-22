package com.chegg.ratelimiter.service;

public class RateLimiterServiceManualTest {

    public static void main(String[] args) {

        RateLimiterService service = new RateLimiterService();

        System.out.println("--- Client A: 3 allowed requests ---");
        for (int i = 1; i <= 3; i++) {
            boolean allowed = service.isAllowed("client-A", "/api/general", 3, 10);
            System.out.println("Client A, request " + i + " allowed? " + allowed);
        }

        System.out.println("--- Client A: 4th request should be blocked ---");
        boolean fourthAllowed = service.isAllowed("client-A", "/api/general", 3, 10);
        System.out.println("Client A, request 4 allowed? " + fourthAllowed);

        System.out.println("--- Client B: fresh bucket, should be allowed ---");
        boolean clientBAllowed = service.isAllowed("client-B", "/api/general", 3, 10);
        System.out.println("Client B, request 1 allowed? " + clientBAllowed);
    }
}