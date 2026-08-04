package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.MovieService;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.core.model.Movie;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 电影管理 — 对应 Python api/admin_movies.py
 */
@RestController
@RequestMapping("/api/admin/movies")
public class AdminMovieController {

    private final MovieService movieService;
    private final SessionMapper sessionMapper;

    public AdminMovieController(MovieService movieService, SessionMapper sessionMapper) {
        this.movieService = movieService;
        this.sessionMapper = sessionMapper;
    }

    @GetMapping
    public Object list(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "50") int limit) {
        var items = movieService.listMovies("rating", status, Math.min(limit, 100));
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        var dto = movieService.getMovieDetail(id);
        if (dto == null) throw BizException.notFound("电影不存在");
        return dto;
    }

    @PostMapping
    public Object create(@RequestBody Movie movie) {
        if (movie.getStatus() == null) movie.setStatus("showing");
        movieService.createMovie(movie);
        return movie;
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Movie movie) {
        movie.setId(id);
        movieService.updateMovie(movie);
        return movie;
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        List<?> sessions = sessionMapper.findByMovieId(id);
        if (sessions != null && !sessions.isEmpty()) {
            throw BizException.badRequest("该电影有 " + sessions.size() + " 个关联排场，无法删除");
        }
        movieService.deleteMovie(id);
        return Map.of("message", "删除成功");
    }

    @PatchMapping("/{id}/status")
    public Object changeStatus(@PathVariable Long id, @RequestParam String status) {
        
        
        
        movieService.updateMovieStatus(id, status);
        return Map.of("message", "状态已更新");
    }
}