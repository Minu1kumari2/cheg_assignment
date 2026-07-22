package com.chegg.ratelimiter.controller;

import com.chegg.ratelimiter.config.RateLimitConfig;
import com.chegg.ratelimiter.dto.StatusResponse;
import com.chegg.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final RateLimiterService rateLimiterService;

    public ApiController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/general")
    public Map<String, String> general() {
        return Map.of("message", "OK");
    }

    @PostMapping("/submit")
    public Map<String, String> submit() {
        return Map.of("message", "OK");
    }

    @GetMapping("/status")
    public StatusResponse status(HttpServletRequest request) {
        String clientId = request.getRemoteAddr();
        String path = "/api/status";
        int[] config = RateLimitConfig.ENDPOINT_LIMITS.get(path);
        int limit = config[0];
        int windowSizeInSeconds = config[1];

        int remaining = rateLimiterService.getRemainingTokens(clientId, path, limit, windowSizeInSeconds);
        long resetAt = rateLimiterService.getResetTimestamp(clientId, path, limit, windowSizeInSeconds);

        return new StatusResponse(limit, remaining, resetAt);
    }
}