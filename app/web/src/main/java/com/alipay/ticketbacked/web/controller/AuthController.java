package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.AuthService;
import com.alipay.ticketbacked.core.model.User;
import com.alipay.ticketbacked.core.model.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * 认证接口 — 对应 Python /api/auth
 * 返回字段用 snake_case 对齐前端: access_token / token_type
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody AuthRequest req) {
        log.info("[register] 收到注册请求, username={}", req.getUsername());
        TokenResponse token = authService.register(req);
        log.info("[register] 注册成功, username={}", req.getUsername());
        return Map.of("access_token", token.getAccessToken(), "token_type", token.getTokenType());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody AuthRequest req) {
        log.info("[login] 收到登录请求, username={}", req.getUsername());
        TokenResponse token = authService.login(req);
        log.info("[login] 登录成功, username={}", req.getUsername());
        return Map.of("access_token", token.getAccessToken(), "token_type", token.getTokenType());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        log.info("[me] currentUser id={}, username={}, gmtCreate={}",
                user.getId(), user.getUsername(), user.getGmtCreate());
        return Map.of("id", user.getId(), "username", user.getUsername(), "created_at", user.getGmtCreate());
    }
}