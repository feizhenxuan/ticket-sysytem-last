package com.alipay.ticketbacked.core.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 电影列表项/详情 DTO — 对应 Python schemas/movie.py
 */
public class MovieDTO {
    private Long id;
    private String title;
    private BigDecimal rating;
    private Integer duration;
    private String genre;
    private String director;
    private String actors;
    private LocalDate releaseDate;
    private String posterUrl;
    private String description;
    private String status;
    private BigDecimal minPrice;     // sort=price 时注入
    private Integer tmdbId;           // MovieDetail 才有

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public String getActors() { return actors; }
    public void setActors(String actors) { this.actors = actors; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }
}