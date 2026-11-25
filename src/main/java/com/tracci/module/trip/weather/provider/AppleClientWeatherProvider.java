package com.tracci.module.trip.weather.provider;

import com.tracci.module.trip.dto.response.TripWeatherLocationResp;
import com.tracci.module.trip.enums.TripWeatherStatus;
import com.tracci.module.trip.enums.WeatherProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppleClientWeatherProvider implements TripWeatherProvider {

    private static final String CLIENT_REQUIRED_MESSAGE = "请使用客户端 WeatherKit 查询目的地天气。";

    @Override
    public WeatherProviderType providerType() {
        return WeatherProviderType.APPLE_CLIENT;
    }

    @Override
    public TripWeatherForecast getForecast(WeatherQuery query) {
        TripWeatherLocationResp location = new TripWeatherLocationResp(
                query.displayCity() != null ? query.displayCity() : query.city(),
                query.latitude(),
                query.longitude(),
                query.country());
        return new TripWeatherForecast(
                TripWeatherStatus.CLIENT_REQUIRED,
                WeatherProvider.APPLE_CLIENT,
                CLIENT_REQUIRED_MESSAGE,
                List.of(),
                location);
    }
}
