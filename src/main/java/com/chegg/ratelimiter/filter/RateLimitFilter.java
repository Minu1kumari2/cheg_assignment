package com.chegg.ratelimiter.filter;

import com.chegg.ratelimiter.config.RateLimitConfig;
import com.chegg.ratelimiter.dto.ErrorResponse;
import com.chegg.ratelimiter.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientId = request.getRemoteAddr();

        int[] config = RateLimitConfig.ENDPOINT_LIMITS.getOrDefault(
            path,
            new int[] { RateLimitConfig.DEFAULT_LIMIT, RateLimitConfig.DEFAULT_WINDOW_SECONDS }
        );
        int limit = config[0];
        int windowSizeInSeconds = config[1];

        boolean allowed = rateLimiterService.isAllowed(clientId, path, limit, windowSizeInSeconds);
        int remaining = rateLimiterService.getRemainingTokens(clientId, path, limit, windowSizeInSeconds);
        long resetAt = rateLimiterService.getResetTimestamp(clientId, path, limit, windowSizeInSeconds);

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(remaining, 0)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetAt));

        if (!allowed) {
            long retryAfter = Math.max(resetAt - (System.currentTimeMillis() / 1000), 1);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(429);
            response.setContentType("application/json");
            ErrorResponse errorBody = new ErrorResponse("Too many requests", retryAfter);
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }

        filterChain.doFilter(request, response);
    }
}