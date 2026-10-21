# Multi-stage Dockerfile for Home Assistant HomeKotlin Add-on
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon || true

COPY src/ src/
RUN ./gradlew installDist --no-daemon

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN apk add --no-cache bash jq curl

COPY --from=builder /src/build/install/homekotlin/ /app/
COPY homekotlin/run.sh /run.sh
RUN chmod +x /run.sh

EXPOSE 8099

ENTRYPOINT ["/run.sh"]
