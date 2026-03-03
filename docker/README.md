# Docker – Local Infrastructure

## Starting the full stack

```bash
# From the docker/ directory
docker compose up -d
```

| Service | URL |
|---|---|
| Kafka broker | `localhost:9092` |
| Kafka UI | http://localhost:8080 |
| PostgreSQL | `localhost:5432` (db: `cryptoflow`, user: `cryptoflow`) |
| market-data-service | http://localhost:8081 |
| portfolio-service | http://localhost:8082 |

## Starting infrastructure only (run services locally)

```bash
docker compose up -d zookeeper kafka kafka-ui postgres
```

Then run each Spring Boot service locally:

```bash
# From the repo root
mvn spring-boot:run -pl market-data-service
mvn spring-boot:run -pl portfolio-service
```

## Stopping

```bash
# Stop containers, keep volumes
docker compose down

# Full reset (removes all data)
docker compose down -v
```
