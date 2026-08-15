FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --create-home vaultsearch
WORKDIR /app
COPY --from=build /workspace/target/vaultsearch-0.1.0-SNAPSHOT.jar /app/vaultsearch.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/vaultsearch.jar"]
