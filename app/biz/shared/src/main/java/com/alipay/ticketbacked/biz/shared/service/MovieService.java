package com.alipay.ticketbacked.biz.shared.service;

import com.alipay.ticketbacked.common.dal.mapper.MovieMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.core.model.Movie;
import com.alipay.ticketbacked.core.model.dto.MovieDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 电影服务 — 对应 Python api/movies.py + agent/tools.py search_movies
 */
@Service
public class MovieService {

    private final MovieMapper movieMapper;
    private final SessionMapper sessionMapper;

    public MovieService(MovieMapper movieMapper, SessionMapper sessionMapper) {
        this.movieMapper = movieMapper;
        this.sessionMapper = sessionMapper;
    }

    /** 电影列表查询，支持三种排序 */
    public List<MovieDTO> listMovies(String sort, String status, int limit) {
        List<Movie> movies;
        if ("price".equals(sort)) {
            movies = movieMapper.findOrderByMinPrice(status, limit);
        } else if ("release".equals(sort)) {
            movies = movieMapper.findByStatusOrderByRelease(status, limit);
        } else {
            movies = movieMapper.findByStatusOrderByRating(status, limit);
        }
        return movies.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public MovieDTO getMovieDetail(Long id) {
        Movie m = movieMapper.findById(id);
        if (m == null) return null;
        return toDTO(m);
    }

    /** 模糊搜索电影 */
    public List<MovieDTO> searchMovies(String keyword, String genre, String sortBy, String status, int limit) {
        List<Movie> movies;
        if (genre != null && !genre.isBlank()) {
            movies = movieMapper.findByGenre(genre, status, limit);
        } else if (keyword != null && !keyword.isBlank()) {
            movies = movieMapper.searchByKeyword(keyword, status, limit);
        } else {
            String sort = sortBy != null ? sortBy : "rating";
            movies = "release".equals(sort)
                    ? movieMapper.findByStatusOrderByRelease(status, limit)
                    : movieMapper.findByStatusOrderByRating(status, limit);
        }
        return movies.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 推荐：正在热映的高分电影 */
    public List<MovieDTO> recommendMovies(String genre, int limit) {
        List<Movie> movies;
        if (genre != null && !genre.isBlank()) {
            movies = movieMapper.findByGenre(genre, "showing", limit);
        } else {
            movies = movieMapper.findByStatusOrderByRating("showing", limit);
        }
        return movies.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private MovieDTO toDTO(Movie m) {
        MovieDTO dto = new MovieDTO();
        dto.setId(m.getId());
        dto.setTitle(m.getTitle());
        dto.setRating(m.getRating());
        dto.setDuration(m.getDuration());
        dto.setGenre(m.getGenre());
        dto.setDirector(m.getDirector());
        dto.setActors(m.getActors());
        dto.setReleaseDate(m.getReleaseDate());
        dto.setPosterUrl(m.getPosterUrl());
        dto.setDescription(m.getDescription());
        dto.setStatus(m.getStatus());
        dto.setTmdbId(m.getTmdbId());
        return dto;
    }

    // ===== Admin CRUD =====
    public void createMovie(Movie movie) { movieMapper.insert(movie); }
    public void updateMovie(Movie movie) { movieMapper.update(movie); }
    public void deleteMovie(Long id) { movieMapper.deleteById(id); }
}