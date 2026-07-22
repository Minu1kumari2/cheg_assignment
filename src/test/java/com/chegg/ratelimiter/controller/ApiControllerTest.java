package com.chegg.ratelimiter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestWithinLimitReturns200WithHeaders() throws Exception {
        mockMvc.perform(get("/api/general").with(req -> { req.setRemoteAddr("10.0.0.1"); return req; }))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"));
    }

    @Test
    void exceedingLimitReturns429WithRetryAfter() throws Exception {
        String ip = "10.0.0.2";
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/general").with(req -> { req.setRemoteAddr(ip); return req; }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/general").with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void submitEndpointBlockedIndependentlyOfGeneralEndpoint() throws Exception {
        String ip = "10.0.0.3";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/submit").with(req -> { req.setRemoteAddr(ip); return req; }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/submit").with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/general").with(req -> { req.setRemoteAddr(ip); return req; }))
                .andExpect(status().isOk());
    }

    @Test
    void differentClientsHaveIndependentCountersOnController() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/submit").with(req -> { req.setRemoteAddr("10.0.0.4"); return req; }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/submit").with(req -> { req.setRemoteAddr("10.0.0.4"); return req; }))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/submit").with(req -> { req.setRemoteAddr("10.0.0.5"); return req; }))
                .andExpect(status().isOk());
    }
}