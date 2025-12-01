# AI Exploration - Perplexity AI REST Client

Spring Boot application providing a REST client for interacting with Perplexity AI, featuring a modern web interface for chat interactions.

## Features

- REST API client for Perplexity AI
- Modern web interface with real-time chat
- Support for multiple Perplexity models
- Docker support for easy deployment
- Configurable API parameters

## Prerequisites

- Java 17 or higher
- Gradle 8.5+ (or use the included wrapper)
- Docker & Docker Compose (optional, for containerized deployment)

## Quick Start

### 1. Clone the repository
```bash
git clone <repository-url>
cd ai-exploration
```

### 2. Configure API Key
```bash
cp .env .env
# Edit .env and add your Perplexity API key
```

### 3. Run with Docker Compose (Recommended)
```bash
docker-compose up --build
```

### 4. Or run locally
```bash
export PERPLEXITY_API_KEY=your-api-key
./gradlew bootRun
```

### 5. Open the application
Navigate to http://localhost:8080 in your browser

## Configuration

Configuration is managed in `src/main/resources/application.properties`:

- `perplexity.api.url` - Perplexity API endpoint
- `perplexity.api.key` - API key (set via environment variable)
- `server.port` - Application port (default: 8080)

## License

This project is provided as-is for educational purposes.

## Contributing

Contributions are welcome. Please open an issue or submit a pull request.