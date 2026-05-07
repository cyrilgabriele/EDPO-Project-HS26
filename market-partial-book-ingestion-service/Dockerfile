FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml .
COPY shared-events/pom.xml shared-events/
COPY market-data-service/pom.xml market-data-service/
COPY market-partial-book-ingestion-service/pom.xml market-partial-book-ingestion-service/
COPY market-order-scout-service/pom.xml market-order-scout-service/
COPY portfolio-service/pom.xml portfolio-service/
COPY transaction-service/pom.xml transaction-service/
COPY user-service/pom.xml user-service/
COPY onboarding-service/pom.xml onboarding-service/

RUN mvn dependency:go-offline -pl market-partial-book-ingestion-service,shared-events -am -q

COPY shared-events/src shared-events/src
COPY market-partial-book-ingestion-service/src market-partial-book-ingestion-service/src

RUN mvn package -pl market-partial-book-ingestion-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /workspace/market-partial-book-ingestion-service/target/*.jar app.jar

EXPOSE 8087

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8087/actuator/health 2>/dev/null | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
