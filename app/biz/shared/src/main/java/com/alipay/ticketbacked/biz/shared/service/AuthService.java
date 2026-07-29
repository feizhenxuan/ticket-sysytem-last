package com.alipay.ticketbacked.biz.shared.service;

import com.alipay.ticketbacked.common.dal.mapper.UserMapper;
import com.alipay.ticketbacked.common.util.BcryptUtil;
import com.alipay.ticketbacked.common.util.JwtUtil;
import com.alipay.ticketbacked.core.model.User;
import com.alipay.ticketbacked.core.model.dto.AuthRequest;
import com.alipay.ticketbacked.core.model.dto.TokenResponse;
import com.alipay.ticketbacked.core.model.dto.UserResponse;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务 — 对应 Python api/auth.py
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final BcryptUtil bcryptUtil;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, BcryptUtil bcryptUtil, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.bcryptUtil = bcryptUtil;
        this.jwtUtil = jwtUtil;
    }

    public TokenResponse register(AuthRequest req) {
        User existing = userMapper.findByUsername(req.getUsername());
        if (existing != null) {
            throw BizException.badRequest("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(bcryptUtil.hashPassword(req.getPassword()));
        user.setIsActive(true);
        userMapper.insert(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", user.getId());
        claims.put("username", user.getUsername());
        return new TokenResponse(jwtUtil.createToken(claims));
    }

    public TokenResponse login(AuthRequest req) {
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null || !bcryptUtil.verifyPassword(req.getPassword(), user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", user.getId());
        claims.put("username", user.getUsername());
        return new TokenResponse(jwtUtil.createToken(claims));
    }

    public UserResponse getCurrentUser(User user) {
        UserResponse resp = new UserResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setCreatedAt(user.getGmtCreate());
        return resp;
    }
}