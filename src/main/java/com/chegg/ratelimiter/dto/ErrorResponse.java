package com.chegg.ratelimiter.dto;

public class ErrorResponse {
    private final String error;
    private final long retryAfterSeconds;

    public ErrorResponse(String error, long retryAfterSeconds) {
        this.error = error;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getError() { return error; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}