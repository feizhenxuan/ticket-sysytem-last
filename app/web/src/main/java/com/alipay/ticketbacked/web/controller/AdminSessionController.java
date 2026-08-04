package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.SessionService;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.common.dal.mapper.SeatMapper;
import com.alipay.ticketbacked.common.dal.mapper.HallMapper;
import com.alipay.ticketbacked.common.dal.mapper.CinemaMapper;
import com.alipay.ticketbacked.common.dal.mapper.OrderMapper;
import com.alipay.ticketbacked.core.model.Session;
import com.alipay.ticketbacked.core.model.Cinema;
import com.alipay.ticketbacked.core.model.Hall;
import com.alipay.ticketbacked.core.model.Seat;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 场次管理 — 对应 Python api/admin_sessions.pyETE FROM hx_session_seats;
 */
@RestController
@RequestMapping("/api/admin/sessions")
public class AdminSessionController {

    private final SessionService sessionService;
    private final SessionMapper sessionMapper;
    private final SessionSeatMapper sessionSeatMapper;
    private final SeatMapper seatMapper;
    private final OrderMapper orderMapper;
    private final HallMapper hallMapper;
    private final CinemaMapper cinemaMapper;

    public AdminSessionController(SessionService sessionService, SessionMapper sessionMapper,
                                   SessionSeatMapper sessionSeatMapper, SeatMapper seatMapper,
                                   HallMapper hallMapper, CinemaMapper cinemaMapper,
                                   OrderMapper orderMapper) {
        this.sessionService = sessionService;
        this.sessionMapper = sessionMapper;
        this.sessionSeatMapper = sessionSeatMapper;
        this.seatMapper = seatMapper;
        this.hallMapper = hallMapper;
        this.cinemaMapper = cinemaMapper;
        this.orderMapper = orderMapper;
    }

    @GetMapping
    public Object list(@RequestParam(required = false) Long movie_id,
                       @RequestParam(required = false) Long cinema_id) {
        var items = sessionService.listAllSessionsForAdmin(movie_id, cinema_id);
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

        // 自动初始化座位：先确保影厅有完整座位，再初始化场次座位状态
        if (session.getId() != null && session.getHallId() != null) {
            Long hallId = session.getHallId();
            Hall hall = hallMapper.findById(hallId);
            int expectedRows = (hall != null && hall.getTotalRows() != null) ? hall.getTotalRows() : 8;
            int expectedCols = (hall != null && hall.getTotalCols() != null) ? hall.getTotalCols() : 12;
            int expectedSeatCount = expectedRows * expectedCols;

            // 检查影厅座位数量是否完整，不够则重新初始化
            List<Seat> existingSeats = seatMapper.findByHallId(hallId);
            if (existingSeats == null || existingSeats.size() < expectedSeatCount) {
                // 先清除旧的不完整座位数据
                seatMapper.deleteByHallId(hallId);
                // 重新按影厅行列数初始化完整座位
                seatMapper.initSeatsForHall(hallId, expectedRows, expectedCols);
            }
            // 初始化场次座位状态
            int seatCount = sessionSeatMapper.initSeatsForSession(session.getId(), hallId);
            System.out.println("[createSession] 场次 " + session.getId() + " 初始化 " + seatCount + " 个座位 (影厅 " + expectedRows + "x" + expectedCols + "=" + expectedSeatCount + ")");
        }

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
        int orderCount = orderMapper.countBySessionId(id);
        if (orderCount > 0) {
            throw BizException.badRequest("该场次有 " + orderCount + " 个关联订单，无法删除");
        }
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

    /**
     * 清空所有场次和座位状态
     * POST /api/admin/sessions/cleanup-all
     */
    @PostMapping("/cleanup-all")
    public Object cleanupAll() {
        // 先删场次（场次表行数少，快）
        int deletedSessions = sessionMapper.deleteAll();
        // 再删座位状态：每次删1个session_id的座位（~96条），循环删
        Long minId = sessionSeatMapper.findMinSessionId();
        Long maxId = sessionSeatMapper.findMaxSessionId();
        int totalDeletedSeats = 0;
        if (minId != null && maxId != null) {
            for (long sid = minId; sid <= maxId; sid++) {
                totalDeletedSeats += sessionSeatMapper.deleteBySessionIdRange(sid, sid);
            }
        }
        return Map.of("deleted_sessions", deletedSessions, "deleted_seat_statuses", totalDeletedSeats);
    }

    /**
     * 清理过期场次 — 删除指定日期之前的所有场次及其座位状态
     * POST /api/admin/sessions/cleanup?before_date=2026-07-30
     */
    @PostMapping("/cleanup")
    public Object cleanupExpired(@RequestParam String before_date) {
        java.time.LocalDateTime before = java.time.LocalDate.parse(before_date).atStartOfDay();
        int deletedSessions = sessionMapper.deleteBeforeDate(before);
        int deletedSeatStatuses = sessionSeatMapper.deleteAllOrphanSeatStatuses();
        return Map.of("before_date", before_date, "deleted_sessions", deletedSessions, "deleted_seat_statuses", deletedSeatStatuses);
    }
}