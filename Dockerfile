# Build the Vue 3 frontend into Spring Boot's static resources.
FROM node:22-alpine AS frontend-builder
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN corepack enable \
    && corepack prepare pnpm@10.15.0 --activate \
    && pnpm install --frozen-lockfile

COPY frontend/ ./
RUN pnpm build:spring

# Build the Spring Boot executable JAR.
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /workspace

COPY pom.xml ./
COPY src/ ./src/
COPY --from=frontend-builder /workspace/src/main/resources/static ./src/main/resources/static

RUN mvn -B -DskipTests package

# Runtime image.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN useradd --system --uid 10001 --create-home appuser \
    && mkdir -p /app/data \
    && chown -R appuser:appuser /app

COPY --from=backend-builder --chown=appuser:appuser \
    /workspace/target/conversation-compiler-0.0.1-SNAPSHOT.jar \
    /app/conversation-compiler.jar

ENV CONVERSATION_COMPILER_DB=/app/data/conversation-compiler.db
ENV JAVA_OPTS=""

VOLUME ["/app/data"]
EXPOSE 8080
USER appuser

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/conversation-compiler.jar"]
