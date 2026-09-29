# syntax=docker/dockerfile:1

# ============================================================
# 构建阶段：用官方 Gradle 镜像拉依赖 + 打包 bootJar
# ============================================================
FROM gradle:9.5.1-jdk21-jammy AS build
WORKDIR /app

# 只复制构建清单，先拉依赖；BuildKit 缓存挂载使改源码后不再重复下载
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle help --no-daemon

# 复制全部源码并打包（跳过测试，纯容器构建）
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle clean bootJar -x test --no-daemon

RUN cp build/libs/*.jar /app/app.jar

# ============================================================
# 运行阶段：仅 JRE，体积小、启动快
# ============================================================
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 3001

# 端口探活（JRE 镜像自带 bash + /dev/tcp，不依赖 curl/wget）
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/3001'

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
