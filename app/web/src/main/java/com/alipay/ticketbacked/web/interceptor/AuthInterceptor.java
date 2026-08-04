package com.alipay.ticketbacked.web.interceptor;

import com.alipay.ticketbacked.common.dal.mapper.UserMapper;
import com.alipay.ticketbacked.common.util.JwtUtil;
import com.alipay.ticketbacked.core.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器 — 对应 Python core/deps.py get_current_user
 * 在 WebConfig 中注册，排除 /api/auth/**, /api/health 等路径。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"缺少认证信息\"}");
            return false;
        }

        String token = auth.substring(7);
        Long userId = jwtUtil.extractUserId(token);
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"无效的认证信息\"}");
            return false;
        }

        log.info("[AuthInterceptor] 开始查询用户, userId={}", userId);
        User user = userMapper.findById(userId);
        log.info("[AuthInterceptor] 查询结果 user={}", user == null ? "null" : "id={}, username={}", user == null ? null : user.getId(), user == null ? null : user.getUsername());
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"用户不存在\"}");
            return false;
        }

        request.setAttribute("currentUser", user);
        return true;
    }
}