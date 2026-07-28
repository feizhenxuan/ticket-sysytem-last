package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.AuthService;
import com.alipay.ticketbacked.core.model.User;
import com.alipay.ticketbacked.core.model.dto.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 认证接口 — 对应 Python /api/auth
 * 返回字段用 snake_case 对齐前端: access_token / token_type
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody AuthRequest req) {
        TokenResponse token = authService.register(req);
        return Map.of("access_token", token.getAccessToken(), "token_type", token.getTokenType());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AuthRequest req) {
        TokenResponse token = authService.login(req);
        return Map.of("access_token", token.getAccessToken(), "token_type", token.getTokenType());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        return Map.of("id", user.getId(), "username", user.getUsername(), "created_at", user.getGmtCreate());
    }
}