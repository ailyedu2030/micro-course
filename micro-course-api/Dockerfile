# Stage 1: Builder
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy pom.xml first for dependency caching
COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests

# Stage 2: Runner
FROM eclipse-temurin:17-jre-alpine AS runner

WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]