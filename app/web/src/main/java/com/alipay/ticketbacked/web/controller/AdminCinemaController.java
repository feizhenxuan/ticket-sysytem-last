package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.CinemaService;
import com.alipay.ticketbacked.common.dal.mapper.HallMapper;
import com.alipay.ticketbacked.common.dal.mapper.CinemaMapper;
import com.alipay.ticketbacked.core.model.Cinema;
import com.alipay.ticketbacked.core.model.Hall;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 影院管理 + 影厅管理 — 对应 Python api/admin_cinemas.py
 */
@RestController
@RequestMapping("/api/admin/cinemas")
public class AdminCinemaController {

    private final CinemaService cinemaService;
    private final HallMapper hallMapper;

    public AdminCinemaController(CinemaService cinemaService, HallMapper hallMapper) {
        this.cinemaService = cinemaService;
        this.hallMapper = hallMapper;
    }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "50") int limit) {
        var items = cinemaService.listCinemas(null, Math.min(limit, 100));
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
        return Map.of("items", halls, "total", halls.size());
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
}