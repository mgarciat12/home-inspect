# syntax=docker/dockerfile:1
# Build context: repository root  ->  docker build -f app/Dockerfile .

# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY auth-server/pom.xml auth-server/
COPY app/pom.xml app/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl app -am dependency:go-offline
COPY app/src app/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl app -am package -DskipTests \
    && cp app/target/app-*.jar /workspace/application.jar

# ---- runtime ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /opt/home-inspect
# heif-thumbnailer converts HEIC photos (iPhone) to PNG before processing
RUN apk add --no-cache libheif-tools libheif-libde265
RUN addgroup -S homeinspect && adduser -S -G homeinspect -u 1001 homeinspect
RUN mkdir -p /data/uploads && chown homeinspect:homeinspect /data/uploads
COPY --from=build /workspace/application.jar application.jar
USER homeinspect
VOLUME /data/uploads
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "/opt/home-inspect/application.jar"]
