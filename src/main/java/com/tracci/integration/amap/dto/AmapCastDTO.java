package com.tracci.integration.amap.dto;

public record AmapCastDTO(
        String date,
        String week,
        String dayweather,
        String nightweather,
        String daytemp,
        String nighttemp,
        String daywind,
        String nightwind,
        String daypower,
        String nightpower
) {
}
