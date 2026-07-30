package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.SessionService;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.common.dal.mapper.SeatMapper;
import com.alipay.ticketbacked.common.dal.mapper.HallMapper;
import com.alipay.ticketbacked.common.dal.mapper.CinemaMapper;
import com.alipay.ticketbacked.core.model.Session;
import com.alipay.ticketbacked.core.model.Cinema;
import com.alipay.ticketbacked.core.model.Hall;
import com.alipay.ticketbacked.core.model.Seat;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 场次管理 — 对应 Python api/admin_sessions.py
 */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final SessionService sessionService;
    private final SessionMapper sessionMapper;
    private final SessionSeatMapper sessionSeatMapper;
    private final SeatMapper seatMapper;
    private final HallMapper hallMapper;
    private final CinemaMapper cinemaMapper;

    public AdminSessionController(SessionService sessionService, SessionMapper sessionMapper,
                                   SessionSeatMapper sessionSeatMapper, SeatMapper seatMapper,
                                   HallMapper hallMapper, CinemaMapper cinemaMapper) {
        this.sessionService = sessionService;
        this.sessionMapper = sessionMapper;
        this.sessionSeatMapper = sessionSeatMapper;
        this.seatMapper = seatMapper;
        this.hallMapper = hallMapper;
        this.cinemaMapper = cinemaMapper;
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

    /**
     * 批量初始化场次座位状态 — 为指定城市的所有场次关联座位
     * GET /api/admin/sessions/init-seats?city=北京
     */
    @GetMapping("/init-seats")
    public Object initSessionSeats(@RequestParam String city, @RequestParam(required = false) Long cinema_id) {
        List<Cinema> cinemas = cinemaMapper.findAll(9999);
        int sessionCount = 0;
        int seatStatusCount = 0;

        for (Cinema cinema : cinemas) {
            if (!city.equals(cinema.getCity())) continue;
            if (cinema_id != null && !cinema_id.equals(cinema.getId())) continue;

            List<Hall> halls = hallMapper.findByCinemaId(cinema.getId());
            for (Hall hall : halls) {
                List<Seat> seats = seatMapper.findByHallId(hall.getId());
                if (seats == null || seats.isEmpty()) continue;

                // 查该影厅的所有场次
                List<Session> sessions = sessionMapper.findByCinemaId(cinema.getId());
                for (Session session : sessions) {
                    if (!hall.getId().equals(session.getHallId())) continue;

                    // 批量创建：一条SQL搞定该场次所有座位
                    int inserted = sessionSeatMapper.initSeatsForSession(session.getId(), hall.getId());
                    seatStatusCount += inserted;
                    sessionCount++;
                }
            }
        }

        return Map.of("city", city, "sessions", sessionCount, "seat_statuses", seatStatusCount);
    }
}