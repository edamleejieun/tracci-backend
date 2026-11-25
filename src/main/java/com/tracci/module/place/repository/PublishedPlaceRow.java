package com.tracci.module.place.repository;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已发布地点列表 JOIN 行。非表实体，仅用于 mapper 映射。
 */
@Getter
@Setter
public class PublishedPlaceRow {

    private String id;
    private String placeScope;
    private String category;
    private String canonicalName;
    private String cityName;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime publishedAt;
    private String highlightsJson;
    private String introduction;
}
