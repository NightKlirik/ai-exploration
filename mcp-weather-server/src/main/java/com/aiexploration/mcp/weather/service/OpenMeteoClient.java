package com.aiexploration.mcp.weather.service;

import com.aiexploration.mcp.weather.model.Location;
import com.aiexploration.mcp.weather.model.WeatherResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class OpenMeteoClient {

    private static final String WEATHER_API_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING_API_URL = "https://geocoding-api.open-meteo.com/v1/search";

    private final WebClient weatherWebClient;
    private final WebClient geocodingWebClient;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.weatherWebClient = WebClient.builder()
                .baseUrl(WEATHER_API_URL)
                .build();
        this.geocodingWebClient = WebClient.builder()
                .baseUrl(GEOCODING_API_URL)
                .build();
    }

    /**
     * Search for locations by name
     *
     * @param name  Location name to search for
     * @param count Maximum number of results (default: 5)
     * @return List of matching locations
     */
    public List<Location> searchLocations(String name, Integer count) {
        try {
            log.info("Searching for locations: {}", name);
            Integer resultCount = (count != null && count > 0) ? count : 5;

            return geocodingWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("name", name)
                            .queryParam("count", resultCount)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        try {
                            // Parse the response to extract results array
                            JsonNode rootNode = objectMapper.readTree(response);
                            JsonNode resultsNode = rootNode.get("results");

                            if (resultsNode != null && resultsNode.isArray()) {
                                return objectMapper.convertValue(
                                        resultsNode,
                                        objectMapper.getTypeFactory().constructCollectionType(List.class, Location.class)
                                );
                            }
                            return Collections.<Location>emptyList();
                        } catch (Exception e) {
                            log.error("Error parsing location search response", e);
                            return Collections.<Location>emptyList();
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error searching for locations: {}", name, e);
                        return Mono.just(Collections.emptyList());
                    })
                    .block();
        } catch (Exception e) {
            log.error("Error searching for locations: {}", name, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get current weather for coordinates
     *
     * @param latitude  Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Current weather data
     */
    public WeatherResponse getCurrentWeather(Double latitude, Double longitude) {
        try {
            log.info("Fetching current weather for coordinates: {}, {}", latitude, longitude);

            return weatherWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .onErrorResume(e -> {
                        log.error("Error fetching current weather for: {}, {}", latitude, longitude, e);
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.error("Error fetching current weather for: {}, {}", latitude, longitude, e);
            return null;
        }
    }

    /**
     * Get weather forecast for coordinates
     *
     * @param latitude  Latitude coordinate
     * @param longitude Longitude coordinate
     * @param days      Number of forecast days (1-16, default: 7)
     * @return Weather forecast data
     */
    public WeatherResponse getForecast(Double latitude, Double longitude, Integer days) {
        try {
            log.info("Fetching {}-day forecast for coordinates: {}, {}", days, latitude, longitude);
            Integer forecastDays = (days != null && days > 0 && days <= 16) ? days : 7;

            return weatherWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,wind_speed_10m_max")
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", forecastDays)
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .onErrorResume(e -> {
                        log.error("Error fetching forecast for: {}, {}", latitude, longitude, e);
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.error("Error fetching forecast for: {}, {}", latitude, longitude, e);
            return null;
        }
    }

    /**
     * Get complete weather data (current + forecast) for coordinates
     *
     * @param latitude  Latitude coordinate
     * @param longitude Longitude coordinate
     * @param days      Number of forecast days (1-16, default: 7)
     * @return Complete weather data with current and daily forecast
     */
    public WeatherResponse getCompleteWeather(Double latitude, Double longitude, Integer days) {
        try {
            log.info("Fetching complete weather data for coordinates: {}, {}", latitude, longitude);
            Integer forecastDays = (days != null && days > 0 && days <= 16) ? days : 7;

            return weatherWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m")
                            .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,wind_speed_10m_max")
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", forecastDays)
                            .build())
                    .retrieve()
                    .bodyToMono(WeatherResponse.class)
                    .onErrorResume(e -> {
                        log.error("Error fetching complete weather for: {}, {}", latitude, longitude, e);
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.error("Error fetching complete weather for: {}, {}", latitude, longitude, e);
            return null;
        }
    }
}
