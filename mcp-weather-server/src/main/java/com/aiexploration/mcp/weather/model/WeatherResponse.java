package com.aiexploration.mcp.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("elevation")
    private Double elevation;

    @JsonProperty("timezone")
    private String timezone;

    @JsonProperty("timezone_abbreviation")
    private String timezoneAbbreviation;

    @JsonProperty("current")
    private CurrentWeather current;

    @JsonProperty("daily")
    private DailyWeather daily;

    @JsonProperty("current_units")
    private Map<String, String> currentUnits;

    @JsonProperty("daily_units")
    private Map<String, String> dailyUnits;
}
