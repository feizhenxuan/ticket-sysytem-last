package com.alipay.ticketbacked.web.interceptor;

import com.alipay.ticketbacked.common.dal.mapper.AdminMapper;
import com.alipay.ticketbacked.common.util.JwtUtil;
import com.alipay.ticketbacked.core.model.Admin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员 JWT 认证拦截器
 * 拦截 /api/admin/** 路径，校验管理员 Token
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    private final JwtUtil jwtUtil;
    private final AdminMapper adminMapper;

    public AdminAuthInterceptor(JwtUtil jwtUtil, AdminMapper adminMapper) {
        this.jwtUtil = jwtUtil;
        this.adminMapper = adminMapper;
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
            response.getWriter().write("{\"detail\":\"缺少管理员认证信息\"}");
            return false;
        }

        String token = auth.substring(7);
        Long adminId = jwtUtil.extractAdminId(token);
        if (adminId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"无效的管理员认证信息\"}");
            return false;
        }

        Admin admin = adminMapper.findById(adminId);
        if (admin == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"管理员账号不存在\"}");
            return false;
        }
        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"detail\":\"管理员账号已被禁用\"}");
            return false;
        }

        request.setAttribute("currentAdmin", admin);
        return true;
    }
}