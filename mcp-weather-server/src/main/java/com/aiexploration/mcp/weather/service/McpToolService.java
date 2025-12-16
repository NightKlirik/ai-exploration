package com.aiexploration.mcp.weather.service;

import com.aiexploration.mcp.weather.model.DailyWeather;
import com.aiexploration.mcp.weather.model.Location;
import com.aiexploration.mcp.weather.model.WeatherResponse;
import com.aiexploration.mcp.weather.model.mcp.ToolDefinition;
import com.aiexploration.mcp.weather.util.WeatherCodeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final OpenMeteoClient openMeteoClient;
    private final ObjectMapper objectMapper;

    /**
     * Get list of available tools
     */
    public List<ToolDefinition> getTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // Tool 1: Search location
        tools.add(ToolDefinition.builder()
                .name("search_location")
                .description("Search for a location by name to get coordinates for weather queries. Returns location details including latitude, longitude, country, and timezone. IMPORTANT: Only works with city names in English.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of(
                                        "type", "string",
                                        "description", "Location name to search for in English only (e.g., 'Berlin', 'New York', 'Tokyo', 'Moscow', 'Paris')"
                                ),
                                "count", Map.of(
                                        "type", "integer",
                                        "description", "Maximum number of results to return (default: 5)",
                                        "default", 5
                                )
                        ),
                        "required", List.of("name")
                ))
                .build());

        // Tool 2: Get current weather
        tools.add(ToolDefinition.builder()
                .name("get_current_weather")
                .description("Get current weather conditions for a specific location using coordinates. Includes temperature, humidity, precipitation, wind, and weather conditions.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "latitude", Map.of(
                                        "type", "number",
                                        "description", "Latitude coordinate (e.g., 52.52)"
                                ),
                                "longitude", Map.of(
                                        "type", "number",
                                        "description", "Longitude coordinate (e.g., 13.41)"
                                )
                        ),
                        "required", List.of("latitude", "longitude")
                ))
                .build());

        // Tool 3: Get weather forecast
        tools.add(ToolDefinition.builder()
                .name("get_weather_forecast")
                .description("Get weather forecast for a specific location using coordinates. Provides daily forecast including temperature range, precipitation, and conditions.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "latitude", Map.of(
                                        "type", "number",
                                        "description", "Latitude coordinate (e.g., 52.52)"
                                ),
                                "longitude", Map.of(
                                        "type", "number",
                                        "description", "Longitude coordinate (e.g., 13.41)"
                                ),
                                "days", Map.of(
                                        "type", "integer",
                                        "description", "Number of forecast days (1-16, default: 7)",
                                        "default", 7,
                                        "minimum", 1,
                                        "maximum", 16
                                )
                        ),
                        "required", List.of("latitude", "longitude")
                ))
                .build());

        return tools;
    }

    /**
     * Execute a tool
     */
    public Object executeTool(String toolName, Map<String, Object> arguments) {
        log.info("Executing tool: {} with arguments: {}", toolName, arguments);

        try {
            return switch (toolName) {
                case "search_location" -> executeSearchLocation(arguments);
                case "get_current_weather" -> executeGetCurrentWeather(arguments);
                case "get_weather_forecast" -> executeGetWeatherForecast(arguments);
                default -> Map.of("error", "Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            return Map.of("error", "Tool execution failed: " + e.getMessage());
        }
    }

    private Object executeSearchLocation(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        Integer count = arguments.containsKey("count") ?
                ((Number) arguments.get("count")).intValue() : 5;

        List<Location> locations = openMeteoClient.searchLocations(name, count);

        return Map.of(
                "content", List.of(
                        Map.of(
                                "type", "text",
                                "text", formatLocationsResponse(locations)
                        )
                )
        );
    }

    private Object executeGetCurrentWeather(Map<String, Object> arguments) {
        Double latitude = ((Number) arguments.get("latitude")).doubleValue();
        Double longitude = ((Number) arguments.get("longitude")).doubleValue();

        WeatherResponse weather = openMeteoClient.getCurrentWeather(latitude, longitude);

        if (weather == null || weather.getCurrent() == null) {
            return Map.of(
                    "content", List.of(
                            Map.of(
                                    "type", "text",
                                    "text", "No weather data found for coordinates: " + latitude + ", " + longitude
                            )
                    )
            );
        }

        return Map.of(
                "content", List.of(
                        Map.of(
                                "type", "text",
                                "text", formatCurrentWeatherResponse(weather)
                        )
                )
        );
    }

    private Object executeGetWeatherForecast(Map<String, Object> arguments) {
        Double latitude = ((Number) arguments.get("latitude")).doubleValue();
        Double longitude = ((Number) arguments.get("longitude")).doubleValue();
        Integer days = arguments.containsKey("days") ?
                ((Number) arguments.get("days")).intValue() : 7;

        WeatherResponse weather = openMeteoClient.getForecast(latitude, longitude, days);

        if (weather == null || weather.getDaily() == null) {
            return Map.of(
                    "content", List.of(
                            Map.of(
                                    "type", "text",
                                    "text", "No forecast data found for coordinates: " + latitude + ", " + longitude
                            )
                    )
            );
        }

        return Map.of(
                "content", List.of(
                        Map.of(
                                "type", "text",
                                "text", formatForecastResponse(weather)
                        )
                )
        );
    }

    private String formatLocationsResponse(List<Location> locations) {
        if (locations == null || locations.isEmpty()) {
            return "No locations found";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(locations.size()).append(" location(s):\n\n");

        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            sb.append(i + 1).append(". ").append(loc.getName());

            if (loc.getCountry() != null) {
                sb.append(", ").append(loc.getCountry());
                if (loc.getCountryCode() != null) {
                    sb.append(" (").append(loc.getCountryCode()).append(")");
                }
            }

            if (loc.getAdmin1() != null) {
                sb.append(" - ").append(loc.getAdmin1());
            }

            sb.append("\n");

            if (loc.getLatitude() != null && loc.getLongitude() != null) {
                sb.append("   Coordinates: ").append(String.format("%.4f", loc.getLatitude()))
                        .append(", ").append(String.format("%.4f", loc.getLongitude())).append("\n");
            }

            if (loc.getTimezone() != null) {
                sb.append("   Timezone: ").append(loc.getTimezone()).append("\n");
            }

            if (loc.getElevation() != null) {
                sb.append("   Elevation: ").append(loc.getElevation()).append("m\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatCurrentWeatherResponse(WeatherResponse weather) {
        StringBuilder sb = new StringBuilder();

        sb.append("Current Weather\n");
        sb.append("═══════════════\n\n");

        if (weather.getLatitude() != null && weather.getLongitude() != null) {
            sb.append("Location: ").append(String.format("%.4f", weather.getLatitude()))
                    .append(", ").append(String.format("%.4f", weather.getLongitude())).append("\n");
        }

        if (weather.getTimezone() != null) {
            sb.append("Timezone: ").append(weather.getTimezone()).append("\n");
        }

        if (weather.getCurrent().getTime() != null) {
            sb.append("Time: ").append(weather.getCurrent().getTime()).append("\n");
        }

        sb.append("\n");

        if (weather.getCurrent().getTemperature2m() != null) {
            sb.append("Temperature: ").append(String.format("%.1f", weather.getCurrent().getTemperature2m())).append("°C");

            if (weather.getCurrent().getApparentTemperature() != null) {
                sb.append(" (feels like ").append(String.format("%.1f", weather.getCurrent().getApparentTemperature())).append("°C)");
            }
            sb.append("\n");
        }

        if (weather.getCurrent().getWeatherCode() != null) {
            int code = weather.getCurrent().getWeatherCode();
            sb.append("Conditions: ").append(WeatherCodeUtil.getDescription(code)).append("\n");
        }

        if (weather.getCurrent().getRelativeHumidity2m() != null) {
            sb.append("Humidity: ").append(weather.getCurrent().getRelativeHumidity2m()).append("%\n");
        }

        if (weather.getCurrent().getPrecipitation() != null) {
            sb.append("Precipitation: ").append(weather.getCurrent().getPrecipitation()).append(" mm\n");
        }

        if (weather.getCurrent().getWindSpeed10m() != null) {
            sb.append("Wind: ").append(String.format("%.1f", weather.getCurrent().getWindSpeed10m())).append(" km/h");

            if (weather.getCurrent().getWindDirection10m() != null) {
                sb.append(" from ").append(weather.getCurrent().getWindDirection10m()).append("°");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatForecastResponse(WeatherResponse weather) {
        StringBuilder sb = new StringBuilder();

        DailyWeather daily = weather.getDaily();
        int days = daily.getTime() != null ? daily.getTime().size() : 0;

        sb.append(days).append("-Day Weather Forecast\n");
        sb.append("═══════════════════════\n\n");

        if (weather.getLatitude() != null && weather.getLongitude() != null) {
            sb.append("Location: ").append(String.format("%.4f", weather.getLatitude()))
                    .append(", ").append(String.format("%.4f", weather.getLongitude())).append("\n");
        }

        sb.append("\n");

        for (int i = 0; i < days; i++) {
            sb.append(daily.getTime().get(i)).append(":\n");

            if (daily.getWeatherCode() != null && i < daily.getWeatherCode().size()) {
                int code = daily.getWeatherCode().get(i);
                sb.append("  Conditions: ").append(WeatherCodeUtil.getDescription(code)).append("\n");
            }

            if (daily.getTemperature2mMin() != null && daily.getTemperature2mMax() != null &&
                    i < daily.getTemperature2mMin().size() && i < daily.getTemperature2mMax().size()) {
                sb.append("  Temperature: ")
                        .append(String.format("%.1f", daily.getTemperature2mMin().get(i)))
                        .append("°C to ")
                        .append(String.format("%.1f", daily.getTemperature2mMax().get(i)))
                        .append("°C\n");
            }

            if (daily.getPrecipitationSum() != null && i < daily.getPrecipitationSum().size()) {
                sb.append("  Precipitation: ")
                        .append(String.format("%.1f", daily.getPrecipitationSum().get(i)))
                        .append(" mm\n");
            }

            if (daily.getWindSpeed10mMax() != null && i < daily.getWindSpeed10mMax().size()) {
                sb.append("  Max Wind: ")
                        .append(String.format("%.1f", daily.getWindSpeed10mMax().get(i)))
                        .append(" km/h\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
