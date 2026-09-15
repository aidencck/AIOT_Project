# ─── 阶段1：提取Spring Boot thin jar分层 ───
FROM eclipse-temurin:17-jdk AS builder
ARG JAR_FILE=./target/*.jar
WORKDIR /build
# BUILDKIT_INLINE_CACHE=1 + 独立stage name per service：避免并行build串JAR_FILE
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract --destination extracted/ 2>&1 | tail -3 || true

# ─── 阶段2：基础系统层（万年不变） ───
FROM eclipse-temurin:17-jdk AS base
ENV TZ=Asia/Shanghai
RUN apt-get update \
 && apt-get install -y --no-install-recommends tzdata curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && cp /usr/share/zoneinfo/${TZ} /etc/localtime \
 && echo "${TZ}" > /etc/timezone \
 && mkdir -p /app/logs /app/cache \
 && groupadd -r spring \
 && useradd -r -g spring springuser \
 && chown -R springuser:spring /app

# ─── 阶段3：依赖层（只有BOOT-INF/lib变化时才重建） ───
FROM base AS deps
WORKDIR /app
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./

# ─── 阶段4：快照层（SNAPSHOT依赖，1~7天一变） ───
FROM deps AS snapshot-deps
COPY --from=builder /build/extracted/snapshot-dependencies/ ./

# ─── 阶段5：应用层（每次代码变更才重建，通常<2MB） ───
FROM snapshot-deps AS app
ARG APP_NAME
ENV JAVA_OPTS="-Xms256m -Xmx256m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxMetaspaceSize=128m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/ -Djava.security.egd=file:/dev/./urandom -Duser.home=/app" \
    SERVER_PORT=8080
COPY --from=builder /build/extracted/application/ ./
LABEL org.opencontainers.image.title="${APP_NAME:-aiot-service}" \
      org.opencontainers.image.source="https://github.com/aidencck/AIOT_Project" \
      aiot.service.name="${APP_NAME:-aiot-service}"
USER springuser
# 注：共享 Dockerfile 无法感知每服务运行时端口，EXPOSE 元数据省略（端口绑定由 compose ports: 权威声明）
ENTRYPOINT ["sh", "-c", "echo Starting ${APP_NAME:-aiot-service} && java ${JAVA_OPTS} -Dapp.name=${APP_NAME:-aiot-service} org.springframework.boot.loader.launch.JarLauncher"]
