package com.tracci.module.trip.service;

import com.tracci.module.trip.dto.response.TripWeatherResponse;

public interface TripWeatherService {

    default TripWeatherResponse getTripWeather(String tripId, String clientPlatform) {
        return getTripWeather(tripId, clientPlatform, null);
    }

    TripWeatherResponse getTripWeather(String tripId, String clientPlatform, String timeZone);
}
