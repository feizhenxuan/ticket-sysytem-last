package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.common.dal.mapper.OrderMapper;
import com.alipay.ticketbacked.common.dal.mapper.UserMapper;
import com.alipay.ticketbacked.core.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理后台 - 用户管理 — 对应 Python api/admin_users.py
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    public AdminUserController(UserMapper userMapper, OrderMapper orderMapper) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) Boolean is_active,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "20") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        List<User> users = userMapper.findAllForAdmin(is_active, search, Math.min(limit, 100), offset);
        int total = userMapper.countAllForAdmin(is_active, search);

        // 给每个用户添加 order_count
        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("is_active", u.getIsActive());
            map.put("gmt_create", u.getGmtCreate());
            map.put("gmt_modify", u.getGmtModify());
            map.put("order_count", orderMapper.countByUserId(u.getId()));
            items.add(map);
        }

        return Map.of("items", items, "total", total);
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