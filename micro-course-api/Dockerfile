FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S microcourse && adduser -S microcourse -G microcourse
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /data/videos /data/slides /app/uploads && chown -R microcourse:microcourse /app /data
USER microcourse
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/admin/stats/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
