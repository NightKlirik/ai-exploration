# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Exploration is a Spring Boot application that provides a REST client for interacting with multiple AI providers:
- Perplexity AI
- HuggingFace
- OpenRouter

The application features a web interface for chat interactions with various AI models and includes detailed token usage tracking for all requests and responses.

## Technology Stack

- Java 17
- Spring Boot 3.2.0
- Gradle 8.5
- Thymeleaf (for web templates)
- Docker & Docker Compose

## Project Structure

This is a multi-module Gradle project:

```
ai-exploration/
├── ai-exploration-app/                      # Main web application
│   └── src/main/java/com/aiexploration/chat/
│       ├── AiExplorationApplication.java    # Main Spring Boot application
│       ├── config/                          # Configuration classes
│       ├── controller/                      # REST API endpoints
│       ├── model/                           # Data models
│       ├── repository/                      # Database repositories
│       └── service/                         # Business logic services
│
└── mcp-weather-server/                      # MCP server for Weather API (Open-Meteo)
    └── src/main/java/com/aiexploration/mcp/weather/
        ├── McpWeatherApplication.java       # MCP server application
        ├── config/                          # MCP server configuration
        ├── controller/                      # MCP JSON-RPC endpoint
        ├── model/                           # Weather and MCP protocol models
        ├── service/                         # Open-Meteo client & tool handlers
        └── util/                            # Weather code utilities
```

## Build and Development

### Build the project
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :ai-exploration-app:build
./gradlew :mcp-weather-server:build
```

### Run the applications

```bash
# Run main application (port 8080)
./gradlew :ai-exploration-app:bootRun

# Run MCP Weather server (port 8081)
./gradlew :mcp-weather-server:bootRun
```

The main application will start on http://localhost:8080
The MCP server will start on http://localhost:8081

### Run tests
```bash
./gradlew test
```

### Run a single test
```bash
./gradlew test --tests "ClassName.methodName"
```

## Docker

### Build and run with Docker Compose
```bash
# Copy .env to .env and add your API key
cp .env .env

# Build and start the container
docker-compose up --build

# Run in detached mode
docker-compose up -d

# Stop the container
docker-compose down
```

### Build Docker image manually
```bash
docker build -t ai-exploration .
```

### Run Docker container manually
```bash
docker run -p 8080:8080 -e PERPLEXITY_API_KEY=your-api-key ai-exploration
```

## Configuration

API configuration is in `src/main/resources/application.properties`:
- `perplexity.api.url` - Perplexity API base URL
- `perplexity.api.key` - API key (use PERPLEXITY_API_KEY environment variable)
- `server.port` - Application port (default: 8080)


## Architecture

### Service Layer
- `AIService` - base interface for all AI providers
- `PerplexityService` - handles communication with Perplexity API
- `HuggingFaceService` - handles communication with HuggingFace API
- `OpenRouterService` - handles communication with OpenRouter API
- All services support multiple models and configurable parameters: max_tokens, temperature, top_p

### REST API Layer
- `ChatController` exposes /api/chat endpoint for chat functionality
- Logs detailed token usage for every request (prompt tokens, completion tokens, total tokens)
- `WebController` serves the web interface at root path

### Web Interface
- Single-page application using vanilla JavaScript
- Real-time chat interface with provider and model selection
- Displays detailed token usage for each response:
  - 📝 Prompt tokens (input)
  - 💬 Completion tokens (output)
  - 🔢 Total tokens
  - ⏱️ Execution time
- Responsive design with gradient styling

### Token Tracking
- All API responses include token usage information
- Token data is displayed in the web interface for each message
- Server logs include detailed token usage for monitoring and debugging
- Supports conversation history tracking per provider
