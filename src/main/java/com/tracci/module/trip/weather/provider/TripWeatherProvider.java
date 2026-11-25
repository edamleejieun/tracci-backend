package com.tracci.module.trip.weather.provider;

public interface TripWeatherProvider {

    WeatherProviderType providerType();

    TripWeatherForecast getForecast(WeatherQuery query);
}
