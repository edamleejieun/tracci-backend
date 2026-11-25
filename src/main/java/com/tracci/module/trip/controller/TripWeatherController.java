package com.tracci.module.trip.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.trip.dto.response.TripWeatherResponse;
import com.tracci.module.trip.service.TripWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripWeatherController {

    private final TripWeatherService tripWeatherService;

    @GetMapping("/{tripId}/weather")
    public ApiResponse<TripWeatherResponse> weather(
            @PathVariable String tripId,
            @RequestParam(required = false) String clientPlatform,
            @RequestParam(required = false) String timeZone) {
        return ApiResponse.ok(tripWeatherService.getTripWeather(tripId, clientPlatform, timeZone));
    }
}
