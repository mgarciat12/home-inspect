# syntax=docker/dockerfile:1
# Build context: repository root  ->  docker build -f auth-server/Dockerfile .

# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY auth-server/pom.xml auth-server/
COPY app/pom.xml app/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl auth-server -am dependency:go-offline
COPY auth-server/src auth-server/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl auth-server -am package -DskipTests \
    && cp auth-server/target/auth-server-*.jar /workspace/application.jar

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /opt/home-inspect
RUN addgroup -S homeinspect && adduser -S -G homeinspect -u 1001 homeinspect
RUN mkdir -p /data/keys && chown homeinspect:homeinspect /data/keys
COPY --from=build /workspace/application.jar application.jar
USER homeinspect
VOLUME /data/keys
EXPOSE 9000
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:9000/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "/opt/home-inspect/application.jar"]
