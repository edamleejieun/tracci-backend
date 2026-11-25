package com.tracci.module.trip.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.trip")
public class TripProperties {

    private DestinationCover destinationCover = new DestinationCover();

    @Getter
    @Setter
    public static class DestinationCover {
        /** 无用户上传封面时，是否按目的地解析系统封面。 */
        private boolean enabled = true;
        /**
         * 国内城市预置封面 COS 前缀。
         * 完整 objectKey：{@code {citiesObjectKeyPrefix}/{cityKey}.jpg}
         * 例：system/destination-covers/v1/cities/chongqing.jpg
         */
        private String citiesObjectKeyPrefix = "system/destination-covers/v1/cities";
        /** 未命中预置城市时使用的默认封面 objectKey。 */
        private String fallbackObjectKey = "system/destination-covers/default.jpg";
        /** 按 cityKey 缓存解析结果（分钟）。 */
        private int cacheTtlMinutes = 4320;
    }
}
