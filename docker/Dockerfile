# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY http-sync ./http-sync
RUN --mount=type=cache,target=/root/.m2 mvn -q -f http-sync/pom.xml -Dmaven.test.skip=true install
ARG SERVICE
COPY ${SERVICE}/ ./service/
RUN --mount=type=cache,target=/root/.m2 mvn -q -f service/pom.xml -Dmaven.test.skip=true package && cp service/target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl openssl && rm -rf /var/lib/apt/lists/*
WORKDIR /app

FROM runtime AS application
COPY --from=build /workspace/app.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
