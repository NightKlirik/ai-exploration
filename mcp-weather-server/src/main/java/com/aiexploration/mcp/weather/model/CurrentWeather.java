package com.aiexploration.mcp.weather.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrentWeather {

    @JsonProperty("time")
    private String time;

    @JsonProperty("interval")
    private Integer interval;

    @JsonProperty("temperature_2m")
    private Double temperature2m;

    @JsonProperty("relative_humidity_2m")
    private Double relativeHumidity2m;

    @JsonProperty("apparent_temperature")
    private Double apparentTemperature;

    @JsonProperty("precipitation")
    private Double precipitation;

    @JsonProperty("weather_code")
    private Integer weatherCode;

    @JsonProperty("wind_speed_10m")
    private Double windSpeed10m;

    @JsonProperty("wind_direction_10m")
    private Integer windDirection10m;
}
