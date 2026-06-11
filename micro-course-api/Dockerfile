# =============================================================================
# Dockerfile · 微课管理平台后端 API
# 多阶段构建: 编译 → 运行
# =============================================================================

# ---- Stage 1: 编译 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

# ---- Stage 2: 运行 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8080/api/auth/me > /dev/null || curl -sf -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
