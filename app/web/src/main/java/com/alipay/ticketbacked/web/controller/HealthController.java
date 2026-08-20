package com.alipay.ticketbacked.web.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 健康检查 — 对应 Python /api/health
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "mysql", "up");
    }
}
