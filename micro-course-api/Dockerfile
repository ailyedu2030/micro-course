# syntax=docker/dockerfile:1.4
FROM --platform=linux/amd64 maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B

FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine
# 渲染依赖：PPT 课件渲染走 Apache POI（JVM AWT），仅需中文字体（font-noto-cjk 与 wqy-zenhei）。
# LibreOffice 非代码依赖（生产实证：无 LibreOffice 环境下 111 个课件渲染成功 status=2），不安装以免镜像膨胀。
# aliyun 镜像加速：审计项 F-2026-08-05-03 验证 dl-cdn 官方源拉取大包不稳定，aliyun 一次成功。
RUN sed -i 's#dl-cdn.alpinelinux.org#mirrors.aliyun.com#g' /etc/apk/repositories \
    && apk add --no-cache ffmpeg tini wqy-zenhei fontconfig font-noto-cjk
RUN addgroup -S microcourse && adduser -S microcourse -G microcourse
WORKDIR /app
COPY --from=build /app/target/micro-course-api-1.0.0.jar app.jar
RUN mkdir -p /data/videos /data/slides /app/uploads /data/uploads/tmp && chown -R microcourse:microcourse /app /data
USER microcourse
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["/sbin/tini", "--", "java", "-jar", "app.jar"]
