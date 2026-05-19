# syntax=docker/dockerfile:1
# Stage 1: Build (Maven + JDK 21)
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Copy parent and module POMs first so dependency layer is cached separately
COPY pom.xml .
COPY shared-events/pom.xml shared-events/
COPY market-data-service/pom.xml market-data-service/
COPY market-partial-book-ingestion-service/pom.xml market-partial-book-ingestion-service/
COPY market-order-scout-service/pom.xml market-order-scout-service/
COPY portfolio-service/pom.xml portfolio-service/
COPY transaction-service/pom.xml transaction-service/
COPY user-service/pom.xml user-service/
COPY onboarding-service/pom.xml onboarding-service/
COPY fx-rate-service/pom.xml fx-rate-service/
COPY coin-metadata-service/pom.xml coin-metadata-service/

RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -pl fx-rate-service,shared-events -am -q

# Copy source
COPY shared-events/src shared-events/src
COPY fx-rate-service/src fx-rate-service/src

RUN --mount=type=cache,target=/root/.m2 mvn -T 1C package -pl fx-rate-service -am -DskipTests -q

# Stage 2: Runtime (JRE 21 slim)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/fx-rate-service/target/*.jar app.jar

EXPOSE 8089

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8089/actuator/health 2>/dev/null | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
