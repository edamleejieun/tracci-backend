package com.tracci.module.trip.weather.provider;

import com.tracci.module.trip.enums.TripWeatherStatus;
import com.tracci.module.trip.enums.WeatherProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DisabledWeatherProvider implements TripWeatherProvider {

    private static final String DISABLED_MESSAGE = "天气服务当前不可用。";

    @Override
    public WeatherProviderType providerType() {
        return WeatherProviderType.DISABLED;
    }

    @Override
    public TripWeatherForecast getForecast(WeatherQuery query) {
        return new TripWeatherForecast(
                TripWeatherStatus.PROVIDER_DISABLED,
                WeatherProvider.DISABLED,
                DISABLED_MESSAGE,
                List.of(),
                null);
    }
}
