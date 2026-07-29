package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.common.dal.mapper.UserMapper;
import com.alipay.ticketbacked.core.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 用户管理 — 对应 Python api/admin_users.py
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserMapper userMapper;

    public AdminUserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Boolean is_active,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "20") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        List<User> users = userMapper.findAllForAdmin(is_active, search, Math.min(limit, 100), offset);
        return Map.of("items", users, "total", users.size());
    }

    @GetMapping("/{id}")
    public User detail(@PathVariable Long id) {
        return userMapper.findById(id);
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> changeStatus(@PathVariable Long id, @RequestParam boolean is_active) {
        userMapper.updateIsActive(id, is_active);
        return Map.of("message", "状态已更新");
    }

    @PutMapping("/{id}/status")
    public Map<String, Object> changeStatusPut(@PathVariable Long id, @RequestParam boolean is_active) {
        userMapper.updateIsActive(id, is_active);
        return Map.of("message", "状态已更新");
    }
}