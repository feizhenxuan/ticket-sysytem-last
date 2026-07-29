package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.SessionService;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.core.model.Session;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台 - 场次管理 — 对应 Python api/admin_sessions.py
 */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final SessionService sessionService;
    private final SessionMapper sessionMapper;

    public AdminSessionController(SessionService sessionService, SessionMapper sessionMapper) {
        this.sessionService = sessionService;
        this.sessionMapper = sessionMapper;
    }

    @GetMapping
    public Object list(@RequestParam(required = false) Long movie_id,
                       @RequestParam(required = false) Long cinema_id) {
        var items = sessionService.listSessions(movie_id, cinema_id, null);
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        var result = sessionService.getSessionDetail(id);
        if (result == null) return Map.of("error", "场次不存在");
        return result;
    }

    @PostMapping
    public Object create(@RequestBody Session session) {
        if (session.getStatus() == null) session.setStatus("available");
        sessionService.createSession(session);
        return session;
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Session session) {
        session.setId(id);
        sessionService.updateSession(session);
        return session;
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return Map.of("message", "删除成功");
    }

    @PatchMapping("/{id}/status")
    public Object changeStatus(@PathVariable Long id, @RequestParam String status) {
        Session session = sessionMapper.findById(id);
        if (session == null) return Map.of("error", "场次不存在");
        session.setStatus(status);
        sessionService.updateSession(session);
        return Map.of("message", "状态已更新");
    }
}