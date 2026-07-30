package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.MovieService;
import com.alipay.ticketbacked.core.model.Movie;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台 - 电影管理 — 对应 Python api/admin_movies.py
 */
@RestController
@RequestMapping("/api/admin/movies")
public class AdminMovieController {

    private final MovieService movieService;

    public AdminMovieController(MovieService movieService) {
        this.movieService = movieService;
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
        movieService.deleteMovie(id);
        return Map.of("message", "删除成功");
    }

    @PatchMapping("/{id}/status")
    public Object changeStatus(@PathVariable Long id, @RequestParam String status) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setStatus(status);
        movieService.updateMovie(movie);
        return Map.of("message", "状态已更新");
    }
}