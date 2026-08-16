FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY LICENSE .
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source="https://github.com/MeherwerAli/vaultsearch" \
      org.opencontainers.image.description="Headless, read-only search projection for Obsidian-style Markdown vaults" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version="0.1.0"
RUN useradd --system --uid 10001 --create-home vaultsearch
WORKDIR /app
COPY --from=build /workspace/target/vaultsearch-0.1.0.jar /app/vaultsearch.jar
COPY LICENSE /licenses/LICENSE
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/vaultsearch.jar"]
