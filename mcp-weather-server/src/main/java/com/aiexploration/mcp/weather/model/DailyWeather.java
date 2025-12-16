package com.aiexploration.mcp.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyWeather {

    @JsonProperty("time")
    private List<String> time;

    @JsonProperty("temperature_2m_max")
    private List<Double> temperature2mMax;

    @JsonProperty("temperature_2m_min")
    private List<Double> temperature2mMin;

    @JsonProperty("precipitation_sum")
    private List<Double> precipitationSum;

    @JsonProperty("weather_code")
    private List<Integer> weatherCode;

    @JsonProperty("wind_speed_10m_max")
    private List<Double> windSpeed10mMax;
}
