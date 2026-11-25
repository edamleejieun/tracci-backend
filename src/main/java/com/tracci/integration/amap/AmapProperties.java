package com.tracci.integration.amap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("tracci.amap")
public class AmapProperties {

    private boolean enabled = true;
    private String webServiceKey;
    private Poi poi = new Poi();
    private Weather weather = new Weather();

    @Getter
    @Setter
    public static class Poi {
        private String endpoint = "https://restapi.amap.com/v5/place/text";
        private String detailEndpoint = "https://restapi.amap.com/v5/place/detail";
        private String searchTypes = "110000";
        private int defaultPageSize = 10;
        private int maxPageSize = 20;
        private int timeoutMs = 3000;
        private int cacheTtlMinutes = 60;
    }

    @Getter
    @Setter
    public static class Weather {
        private boolean enabled = true;
        private String endpoint = "https://restapi.amap.com/v3/weather/weatherInfo";
        private int timeoutMs = 3000;
        private int maxForecastDays = 4;
        private int maxSupportedDays = 4;
        private int cacheTtlMinutes = 60;
        private int unavailableCacheTtlMinutes = 360;
        private int providerErrorCacheTtlMinutes = 3;
    }
}
