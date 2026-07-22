package com.chegg.ratelimiter.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    @Test
    void allowsRequestsWithinLimit() {
        RateLimiterService service = new RateLimiterService();
        for (int i = 0; i < 3; i++) {
            assertTrue(service.isAllowed("client-1", "/api/general", 3, 10));
        }
    }

    @Test
    void exactlyNthRequestPassesAndNPlus1thFails() {
        RateLimiterService service = new RateLimiterService();
        for (int i = 1; i <= 5; i++) {
            assertTrue(service.isAllowed("client-2", "/api/general", 5, 10), "Request " + i + " should pass");
        }
        assertFalse(service.isAllowed("client-2", "/api/general", 5, 10), "6th request should fail");
    }

    @Test
    void differentClientsHaveIndependentCounters() {
        RateLimiterService service = new RateLimiterService();
        for (int i = 0; i < 3; i++) {
            service.isAllowed("client-A", "/api/general", 3, 10);
        }
        assertFalse(service.isAllowed("client-A", "/api/general", 3, 10));
        assertTrue(service.isAllowed("client-B", "/api/general", 3, 10));
    }

    @Test
    void differentEndpointsHaveIndependentLimitsForSameClient() {
        RateLimiterService service = new RateLimiterService();
        for (int i = 0; i < 5; i++) {
            assertTrue(service.isAllowed("client-1", "/api/general", 20, 60));
        }
        assertTrue(service.isAllowed("client-1", "/api/submit", 5, 60));
    }

    @Test
    void clientCanMakeRequestsAgainAfterWindowResets() throws InterruptedException {
        RateLimiterService service = new RateLimiterService();
        for (int i = 0; i < 2; i++) {
            service.isAllowed("client-1", "/api/general", 2, 1); // limit 2, window 1 second
        }
        assertFalse(service.isAllowed("client-1", "/api/general", 2, 1));
        Thread.sleep(1100);
        assertTrue(service.isAllowed("client-1", "/api/general", 2, 1));
    }
}