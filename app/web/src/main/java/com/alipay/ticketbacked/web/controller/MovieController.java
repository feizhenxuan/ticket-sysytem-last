package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.MovieService;
import com.alipay.ticketbacked.core.model.Movie;
import com.alipay.ticketbacked.core.model.dto.MovieDTO;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 电影接口 — 对应 Python /api/movies
 */
@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public Map<String, Object> listMovies(
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "showing") String status,
            @RequestParam(defaultValue = "10") int limit) {
        List<MovieDTO> items = movieService.listMovies(sort, status, Math.min(limit, 50));
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public MovieDTO getMovie(@PathVariable Long id) {
        MovieDTO dto = movieService.getMovieDetail(id);
        if (dto == null) throw BizException.notFound("电影不存在");
        return dto;
    }
}