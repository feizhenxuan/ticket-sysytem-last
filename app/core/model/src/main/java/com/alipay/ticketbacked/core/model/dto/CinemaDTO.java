package com.alipay.ticketbacked.core.model.dto;

import java.math.BigDecimal;

/**
 * 影院列表项 DTO — 对应 Python schemas/cinema.py
 */
public class CinemaDTO {
    private Long id;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String city;
    private Integer distance; // 米，当请求带 lat/lng 时计算注入

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Integer getDistance() { return distance; }
    public void setDistance(Integer distance) { this.distance = distance; }
}