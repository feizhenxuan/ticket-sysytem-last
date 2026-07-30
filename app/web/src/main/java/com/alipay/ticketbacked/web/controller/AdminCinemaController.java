package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.CinemaService;
import com.alipay.ticketbacked.common.dal.mapper.HallMapper;
import com.alipay.ticketbacked.common.dal.mapper.SeatMapper;
import com.alipay.ticketbacked.common.dal.mapper.CinemaMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.core.model.Cinema;
import com.alipay.ticketbacked.core.model.Hall;
import com.alipay.ticketbacked.core.model.Seat;
import com.alipay.ticketbacked.core.model.Session;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 管理后台 - 影院管理 + 影厅管理 — 对应 Python api/admin_cinemas.py
 */
@RestController
@RequestMapping("/api/admin/cinemas")
public class AdminCinemaController {

    private final CinemaService cinemaService;
    private final HallMapper hallMapper;
    private final SeatMapper seatMapper;
    private final CinemaMapper cinemaMapper;
    private final SessionMapper sessionMapper;
    private final SessionSeatMapper sessionSeatMapper;

    public AdminCinemaController(CinemaService cinemaService, HallMapper hallMapper,
                                  SeatMapper seatMapper, CinemaMapper cinemaMapper,
                                  SessionMapper sessionMapper, SessionSeatMapper sessionSeatMapper) {
        this.cinemaService = cinemaService;
        this.hallMapper = hallMapper;
        this.seatMapper = seatMapper;
        this.cinemaMapper = cinemaMapper;
        this.sessionMapper = sessionMapper;
        this.sessionSeatMapper = sessionSeatMapper;
    }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "50") int limit) {
        var cinemas = cinemaService.listCinemas(null, Math.min(limit, 100));
        var items = cinemas.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("address", c.getAddress());
            map.put("longitude", c.getLongitude());
            map.put("latitude", c.getLatitude());
            map.put("phone", c.getPhone());
            map.put("city", c.getCity());
            map.put("hall_count", hallMapper.findByCinemaId(c.getId()).size());
            return map;
        }).collect(Collectors.toList());
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        var dto = cinemaService.getCinema(id);
        if (dto == null) throw BizException.notFound("影院不存在");
        return dto;
    }

    @PostMapping
    public Object create(@RequestBody Cinema cinema) {
        cinemaService.createCinema(cinema);
        return cinema;
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Cinema cinema) {
        cinema.setId(id);
        cinemaService.updateCinema(cinema);
        return cinema;
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        cinemaService.deleteCinema(id);
        return Map.of("message", "删除成功");
    }

    // ===== Halls =====

    @GetMapping("/{cinemaId}/halls")
    public Object listHalls(@PathVariable Long cinemaId) {
        List<Hall> halls = hallMapper.findByCinemaId(cinemaId);
        var items = halls.stream().map(h -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", h.getId());
            map.put("cinema_id", h.getCinemaId());
            map.put("name", h.getName());
            map.put("hall_type", h.getHallType());
            map.put("total_rows", h.getTotalRows());
            map.put("total_cols", h.getTotalCols());
            map.put("seat_count", (h.getTotalRows() != null && h.getTotalCols() != null) ? h.getTotalRows() * h.getTotalCols() : 0);
            return map;
        }).collect(Collectors.toList());
        return Map.of("items", items, "total", items.size());
    }

    @PostMapping("/{cinemaId}/halls")
    public Object createHall(@PathVariable Long cinemaId, @RequestBody Hall hall) {
        hall.setCinemaId(cinemaId);
        if (hall.getHallType() == null) hall.setHallType("normal");
        if (hall.getTotalRows() == null) hall.setTotalRows(8);
        if (hall.getTotalCols() == null) hall.setTotalCols(12);
        hallMapper.insert(hall);
        return hall;
    }

    @PutMapping("/halls/{hallId}")
    public Object updateHall(@PathVariable Long hallId, @RequestBody Hall hall) {
        hall.setId(hallId);
        hallMapper.update(hall);
        return hall;
    }

    @DeleteMapping("/halls/{hallId}")
    public Object deleteHall(@PathVariable Long hallId) {
        hallMapper.deleteById(hallId);
        return Map.of("message", "删除成功");
    }

    /**
     * 批量初始化座位 — 为指定城市的所有影厅生成座位
     * GET /api/admin/cinemas/init-seats?city=北京
     */
    @GetMapping("/init-seats")
    public Object initSeats(@RequestParam String city, @RequestParam(required = false) Long cinema_id) {
        List<Cinema> cinemas = cinemaMapper.findAll(9999);
        int hallCount = 0;
        int seatCount = 0;

        for (Cinema cinema : cinemas) {
            if (!city.equals(cinema.getCity())) continue;
            if (cinema_id != null && !cinema_id.equals(cinema.getId())) continue;

            List<Hall> halls = hallMapper.findByCinemaId(cinema.getId());
            for (Hall hall : halls) {
                // 检查是否已有座位
                List<Seat> existing = seatMapper.findByHallId(hall.getId());
                if (existing != null && !existing.isEmpty()) continue;

                int rows = hall.getTotalRows() != null ? hall.getTotalRows() : 8;
                int cols = hall.getTotalCols() != null ? hall.getTotalCols() : 12;

                seatCount += seatMapper.initSeatsForHall(hall.getId(), rows, cols);
                hallCount++;
            }
        }

        return Map.of("city", city, "halls", hallCount, "seats", seatCount);
    }

    /**
     * 超级批量初始化 — 一次调用完成一个城市的全部数据:
     * 影厅 + 座位 + 场次 + 场次座位状态
     * GET /api/admin/cinemas/batch-init?city=石家庄&date=2026-07-30
     */
    @PostMapping("/batch-init")
    public Object batchInit(@RequestParam String city, @RequestParam(defaultValue = "2026-07-30") String date,
                            @RequestParam(defaultValue = "2026-07-31") String date2) {
        List<Cinema> allCinemas = cinemaMapper.findAll(9999);
        int hallCount = 0, seatCount = 0, sessionCount = 0, seatStatusCount = 0;

        Long[] movieIds = {17L, 44L, 50L, 1L, 56L};
        String[][] timeSlots = {
            {"10:00:00", "12:00:00", "38.0"},
            {"14:00:00", "16:00:00", "42.0"},
            {"18:00:00", "20:00:00", "48.0"}
        };

        for (Cinema cinema : allCinemas) {
            if (!city.equals(cinema.getCity())) continue;

            // 1. 查已有影厅，没有就创建
            List<Hall> halls = hallMapper.findByCinemaId(cinema.getId());
            if (halls == null || halls.isEmpty()) {
                Hall hall = new Hall();
                hall.setCinemaId(cinema.getId());
                hall.setName("1号厅");
                hall.setHallType("normal");
                hall.setTotalRows(8);
                hall.setTotalCols(12);
                hallMapper.insert(hall);
                halls = new ArrayList<>();
                halls.add(hall);
                hallCount++;
            }

            Hall hall = halls.get(0);
            int rows = hall.getTotalRows() != null ? hall.getTotalRows() : 8;
            int cols = hall.getTotalCols() != null ? hall.getTotalCols() : 12;

            // 2. 批量创建座位（一条SQL）
            List<Seat> existingSeats = seatMapper.findByHallId(hall.getId());
            if (existingSeats == null || existingSeats.isEmpty()) {
                seatCount += seatMapper.initSeatsForHall(hall.getId(), rows, cols);
            }

            // 3. 创建场次（2天 x 3场/天 = 6场）
            int mi = 0;
            for (String d : new String[]{date, date2}) {
                for (String[] slot : timeSlots) {
                    Session session = new Session();
                    session.setMovieId(movieIds[mi % movieIds.length]);
                    session.setCinemaId(cinema.getId());
                    session.setHallId(hall.getId());
                    session.setStartTime(java.time.LocalDateTime.parse(d + "T" + slot[0]));
                    session.setEndTime(java.time.LocalDateTime.parse(d + "T" + slot[1]));
                    session.setPrice(new java.math.BigDecimal(slot[2]));
                    session.setStatus("available");
                    try {
                        sessionMapper.insert(session);
                        sessionCount++;
                        // 4. 批量创建场次座位状态（一条SQL）
                        sessionSeatMapper.initSeatsForSession(session.getId(), hall.getId());
                        seatStatusCount += rows * cols;
                    } catch (Exception ignored) {
                        // This catch statement is intentionally empty
                    }
                    mi++;
                }
            }
        }

        return Map.of("city", city, "halls", hallCount, "seats", seatCount,
                       "sessions", sessionCount, "seat_statuses", seatStatusCount);
    }
}