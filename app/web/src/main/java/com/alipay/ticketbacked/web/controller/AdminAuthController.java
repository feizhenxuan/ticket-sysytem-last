package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.AdminAuthService;
import com.alipay.ticketbacked.core.model.Admin;
import com.alipay.ticketbacked.core.model.dto.AuthRequest;
import com.alipay.ticketbacked.core.model.dto.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 管理员认证接口
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AuthRequest req) {
        log.info("[admin login] 收到管理员登录请求, username={}", req.getUsername());
        TokenResponse token = adminAuthService.login(req);
        return Map.of("access_token", token.getAccessToken(), "token_type", token.getTokenType());
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody AuthRequest req) {
        log.info("[admin register] 收到管理员注册请求, username={}", req.getUsername());
        TokenResponse token = adminAuthService.register(req);
        return Map.of("access_token", token.getAccessToken(), "token_type", token.getTokenType());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Admin admin = (Admin) request.getAttribute("currentAdmin");
        return Map.of(
                "id", admin.getId(),
                "username", admin.getUsername(),
                "role", admin.getRole()
        );
    }
}