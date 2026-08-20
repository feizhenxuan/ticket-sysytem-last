package com.alipay.ticketbacked.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台 - 系统配置 — 对应 Python api/admin_config.py
 * 补齐前端期望的 update / reset 接口。
 */
@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    @Value("${app.alipay.app-id:}")
    private String alipayAppId;

    @Value("${app.amap.api-key:}")
    private String amapApiKey;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String llmModel;

    // 默认配置（模拟 Python admin_config 的数据库存储）
    private Map<String, Object> configStore = new LinkedHashMap<>();

    public AdminConfigController() {
        configStore.put("site_name", "智能购票助手");
        configStore.put("announcement", "");
        configStore.put("ticket_order_timeout", 300);
        configStore.put("max_seats_per_order", 6);
        configStore.put("enable_chat", true);
        configStore.put("enable_payment", true);
        configStore.put("enable_movie_review", false);
        configStore.put("maintenance_mode", false);
    }

    @GetMapping
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new LinkedHashMap<>(configStore);
        result.put("alipay_app_id", alipayAppId);
        result.put("amap_api_key", amapApiKey != null && !amapApiKey.isBlank() ? "***" : "");
        result.put("llm_model", llmModel);
        return result;
    }

    /** 前端期望: PUT /api/admin/config */
    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> data) {
        configStore.putAll(data);
        return Map.of("message", "配置已保存", "data", configStore);
    }

    /** 前端期望: POST /api/admin/config/reset */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        configStore.put("site_name", "智能购票助手");
        configStore.put("announcement", "");
        configStore.put("ticket_order_timeout", 300);
        configStore.put("max_seats_per_order", 6);
        configStore.put("enable_chat", true);
        configStore.put("enable_payment", true);
        configStore.put("enable_movie_review", false);
        configStore.put("maintenance_mode", false);
        return configStore;
    }
}
