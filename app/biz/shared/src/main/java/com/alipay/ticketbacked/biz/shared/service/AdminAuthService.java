package com.alipay.ticketbacked.biz.shared.service;

import com.alipay.ticketbacked.common.dal.mapper.AdminMapper;
import com.alipay.ticketbacked.common.util.BcryptUtil;
import com.alipay.ticketbacked.common.util.JwtUtil;
import com.alipay.ticketbacked.core.model.Admin;
import com.alipay.ticketbacked.core.model.dto.AuthRequest;
import com.alipay.ticketbacked.core.model.dto.TokenResponse;
import com.alipay.ticketbacked.core.model.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员认证服务
 */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final AdminMapper adminMapper;
    private final BcryptUtil bcryptUtil;
    private final JwtUtil jwtUtil;

    public AdminAuthService(AdminMapper adminMapper, BcryptUtil bcryptUtil, JwtUtil jwtUtil) {
        this.adminMapper = adminMapper;
        this.bcryptUtil = bcryptUtil;
        this.jwtUtil = jwtUtil;
    }

    public TokenResponse login(AuthRequest req) {
        log.info("[admin login] 开始查询管理员, username={}", req.getUsername());
        Admin admin = adminMapper.findByUsername(req.getUsername());
        if (admin == null) {
            throw BizException.unauthorized("管理员账号或密码错误");
        }
        boolean verified = bcryptUtil.verifyPassword(req.getPassword(), admin.getPasswordHash());
        if (!verified) {
            throw BizException.unauthorized("管理员账号或密码错误");
        }
        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw BizException.badRequest("管理员账号已被禁用");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin_id", admin.getId());
        claims.put("username", admin.getUsername());
        claims.put("role", admin.getRole());
        String token = jwtUtil.createToken(claims);
        log.info("[admin login] 登录成功, username={}", req.getUsername());
        return new TokenResponse(token);
    }

    public TokenResponse register(AuthRequest req) {
        log.info("[admin register] 开始查询管理员是否存在, username={}", req.getUsername());
        Admin existing = adminMapper.findByUsername(req.getUsername());
        if (existing != null) {
            throw BizException.badRequest("管理员用户名已存在");
        }
        Admin admin = new Admin();
        admin.setUsername(req.getUsername());
        admin.setPasswordHash(bcryptUtil.hashPassword(req.getPassword()));
        admin.setRole("admin");
        admin.setIsActive(true);
        adminMapper.insert(admin);
        log.info("[admin register] 注册成功, adminId={}", admin.getId());

        Map<String, Object> claims = new HashMap<>();
        claims.put("admin_id", admin.getId());
        claims.put("username", admin.getUsername());
        claims.put("role", admin.getRole());
        String token = jwtUtil.createToken(claims);
        return new TokenResponse(token);
    }
}