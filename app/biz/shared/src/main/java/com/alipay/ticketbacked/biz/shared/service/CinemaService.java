package com.alipay.ticketbacked.biz.shared.service;

import com.alipay.ticketbacked.common.dal.mapper.CinemaMapper;
import com.alipay.ticketbacked.core.model.Cinema;
import com.alipay.ticketbacked.core.model.dto.CinemaDTO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 影院服务 — 对应 Python api/cinemas.py
 */
@Service
public class CinemaService {

    private final CinemaMapper cinemaMapper;

    public CinemaService(CinemaMapper cinemaMapper) {
        this.cinemaMapper = cinemaMapper;
    }

    /**
     * 查询附近影院：有城市关键词时按城市过滤，有坐标时计算距离排序；
     * radius（米）非空时仅返回距离 ≤ radius 的影院
     */
    public List<CinemaDTO> listCinemasNearby(String city, Double lat, Double lng, Integer radius, int limit) {
        List<Cinema> cinemas;
        if (city != null && !city.isBlank()) {
            // 有城市关键词：模糊匹配城市，再按距离排序
            cinemas = cinemaMapper.findByCityKeyword(city, 200);
        } else if (lat != null && lng != null) {
            // 无城市但有坐标：取全部按距离排
            cinemas = cinemaMapper.findAllNoLimit();
        } else {
            cinemas = cinemaMapper.findAll(limit);
        }

        final Double userLat = lat;
        final Double userLng = lng;

        List<CinemaDTO> dtos = cinemas.stream().map(this::toDTO).collect(Collectors.toList());

        // 如果有经纬度，计算距离并按距离排序
        if (userLat != null && userLng != null) {
            dtos.forEach(dto -> {
                if (dto.getLatitude() != null && dto.getLongitude() != null) {
                    int dist = (int) Math.round(haversine(
                            userLat, userLng,
                            dto.getLatitude().doubleValue(), dto.getLongitude().doubleValue()));
                    dto.setDistance(dist);
                }
            });
            // radius（米）非空时，先剔除超出半径的影院
            if (radius != null && radius > 0) {
                dtos = dtos.stream()
                        .filter(dto -> dto.getDistance() != null && dto.getDistance() <= radius)
                        .collect(Collectors.toList());
            }
            dtos = dtos.stream()
                    .sorted(Comparator.comparing(CinemaDTO::getDistance, Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            dtos = dtos.stream().limit(limit).collect(Collectors.toList());
        }

        return dtos;
    }

    public List<CinemaDTO> listCinemas(String city, int limit) {
        List<Cinema> cinemas;
        if (city != null && !city.isBlank()) {
            cinemas = cinemaMapper.findByCity(city, limit);
        } else {
            cinemas = cinemaMapper.findAll(limit);
        }
        return cinemas.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public CinemaDTO getCinema(Long id) {
        Cinema c = cinemaMapper.findById(id);
        return c == null ? null : toDTO(c);
    }

    public List<CinemaDTO> searchByName(String name, int limit) {
        return cinemaMapper.searchByName(name, limit).stream().map(this::toDTO).collect(Collectors.toList());
    }

    private CinemaDTO toDTO(Cinema c) {
        CinemaDTO dto = new CinemaDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setAddress(c.getAddress());
        dto.setLongitude(c.getLongitude());
        dto.setLatitude(c.getLatitude());
        dto.setPhone(c.getPhone());
        dto.setCity(c.getCity());
        return dto;
    }

    /**
     * Haversine 公式计算两点间距离（米）
     */
    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000; // 地球半径，米
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ===== Admin CRUD =====
    public void createCinema(Cinema cinema) { cinemaMapper.insert(cinema); }
    public void updateCinema(Cinema cinema) { cinemaMapper.update(cinema); }
    public void deleteCinema(Long id) { cinemaMapper.deleteById(id); }
}