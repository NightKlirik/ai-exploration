# MCP Weather Server

MCP (Model Context Protocol) сервер для работы с Open-Meteo API - предоставляет данные о погоде и прогнозы.

## Описание

Этот модуль предоставляет MCP-сервер, который позволяет AI-агентам получать информацию о погоде через Open-Meteo API (open-meteo.com). Open-Meteo - это бесплатный API для прогнозов погоды с открытым исходным кодом, не требующий API ключа.

## Возможности

Сервер предоставляет следующие инструменты (tools):

1. **search_location** - Поиск местоположения по названию для получения координат
   - Возвращает широту, долготу, страну и часовой пояс
   - Поддерживает поиск городов по всему миру

2. **get_current_weather** - Получить текущую погоду для указанных координат
   - Температура, влажность, осадки
   - Скорость и направление ветра
   - Погодные условия (ясно, облачно, дождь, снег и т.д.)

3. **get_weather_forecast** - Получить прогноз погоды на 1-16 дней
   - Максимальная и минимальная температура
   - Сумма осадков
   - Максимальная скорость ветра
   - Погодные условия

## Быстрый старт

### Запуск сервера

```bash
# Из корня проекта
./gradlew :mcp-weather-server:bootRun

# Или из директории модуля
cd mcp-weather-server
../gradlew bootRun
```

Сервер запустится на порту 8081.

### Проверка работоспособности

```bash
curl http://localhost:8081/mcp/health
```

## Примеры использования

### Инициализация соединения

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }'
```

### Получение списка инструментов

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/list",
    "params": {}
  }'
```

### Вызов инструмента - Поиск местоположения

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "search_location",
      "arguments": {
        "name": "Berlin",
        "count": 5
      }
    }
  }'
```

### Вызов инструмента - Текущая погода

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "get_current_weather",
      "arguments": {
        "latitude": 52.52,
        "longitude": 13.41
      }
    }
  }'
```

### Вызов инструмента - Прогноз погоды

```bash
curl -X POST http://localhost:8081/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": "5",
    "method": "tools/call",
    "params": {
      "name": "get_weather_forecast",
      "arguments": {
        "latitude": 52.52,
        "longitude": 13.41,
        "days": 7
      }
    }
  }'
```

## Интеграция с основным приложением

Чтобы использовать этот MCP-сервер в основном приложении AI Exploration, добавьте следующую конфигурацию:

```java
McpServerConfig weatherConfig = McpServerConfig.builder()
    .id("weather")
    .name("Weather MCP Server")
    .url("http://localhost:8081/mcp")
    .build();

// Инициализация и использование через McpClientService
mcpClientService.initializeConnection(weatherConfig);
List<McpTool> tools = mcpClientService.listTools(weatherConfig);
```

## Технологии

- Spring Boot 3.2.0
- Spring WebFlux (для HTTP-клиента)
- Jackson (для работы с JSON)
- Lombok
- Open-Meteo API (бесплатный, без API ключа)

## Структура проекта

```
mcp-weather-server/
├── src/main/java/com/aiexploration/mcp/weather/
│   ├── McpWeatherApplication.java           # Главный класс приложения
│   ├── config/
│   │   └── WebConfig.java                   # Конфигурация Spring
│   ├── controller/
│   │   └── McpServerController.java         # MCP JSON-RPC endpoint
│   ├── model/
│   │   ├── Location.java                    # Модель местоположения
│   │   ├── WeatherResponse.java             # Модель ответа погоды
│   │   ├── CurrentWeather.java              # Модель текущей погоды
│   │   ├── DailyWeather.java                # Модель дневного прогноза
│   │   └── mcp/                             # MCP протокол модели
│   │       ├── JsonRpcRequest.java
│   │       ├── JsonRpcResponse.java
│   │       └── ToolDefinition.java
│   ├── service/
│   │   ├── OpenMeteoClient.java             # HTTP-клиент для Open-Meteo API
│   │   └── McpToolService.java              # Обработка MCP инструментов
│   └── util/
│       └── WeatherCodeUtil.java             # Утилита для интерпретации кодов погоды
└── src/main/resources/
    └── application.properties               # Конфигурация приложения
```

## Коды погоды WMO

Сервер использует стандартные коды погоды WMO (Всемирная метеорологическая организация):
- 0: Ясное небо
- 1-3: Переменная облачность
- 45-48: Туман
- 51-65: Дождь различной интенсивности
- 71-86: Снег различной интенсивности
- 95-99: Гроза

## Лицензия

Этот проект является частью AI Exploration.
