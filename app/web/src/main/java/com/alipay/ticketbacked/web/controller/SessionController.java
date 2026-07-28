package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.SessionService;
import com.alipay.ticketbacked.core.model.dto.SessionDTO;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 场次接口 — 对应 Python /api/sessions
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public Map<String, Object> listSessions(
            @RequestParam(required = false) Long movie_id,
            @RequestParam(required = false) Long cinema_id,
            @RequestParam(required = false) String date) {
        List<SessionDTO> items = sessionService.listSessions(movie_id, cinema_id, date);
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Map<String, Object> getSession(@PathVariable Long id) {
        Map<String, Object> result = sessionService.getSessionDetail(id);
        if (result == null) throw BizException.notFound("场次不存在");
        return result;
    }

    @GetMapping("/{id}/seats")
    public Map<String, Object> getSessionSeats(@PathVariable Long id) {
        List<Map<String, Object>> seats = sessionService.getSessionSeats(id);
        return Map.of("session_id", id, "seats", seats);
    }
}