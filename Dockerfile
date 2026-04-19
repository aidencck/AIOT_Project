# ==========================================
# 第一阶段：构建并提取分层 (Builder Stage)
# ==========================================
FROM eclipse-temurin:17-jre-alpine AS builder

WORKDIR /build

# 接收构建参数
ARG JAR_FILE=target/*.jar
ARG APP_NAME="app.jar"

# 复制 JAR 包
COPY ${JAR_FILE} ${APP_NAME}

# 使用 Spring Boot 提供的 layertools 提取分层目录
# Spring Boot 3.x 默认支持 layertools
RUN java -Djarmode=layertools -jar ${APP_NAME} extract

# ==========================================
# 第二阶段：运行阶段 (Run Stage)
# ==========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 设置环境变量
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/ -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080

# 配置时区和基础依赖
RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone \
    && mkdir -p /app/logs

# 按照层级复制文件，利用 Docker 缓存加速构建 (频率低的依赖在上层，频率高的代码在下层)
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# 暴露端口 (可通过 docker-compose 覆盖或映射)
EXPOSE ${SERVER_PORT}

# Spring Boot 3.x 使用的启动类是 org.springframework.boot.loader.launch.JarLauncher
# 相比直接 java -jar，分层启动速度更快，镜像复用率更高
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} org.springframework.boot.loader.launch.JarLauncher"]
