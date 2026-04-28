FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=target/*.jar

# 设置环境变量
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/ -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT=8080

# 配置时区和基础依赖，并创建非特权用户
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata curl \
    && rm -rf /var/lib/apt/lists/* \
    && cp /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone \
    && mkdir -p /app/logs \
    && groupadd -r spring \
    && useradd -r -g spring springuser \
    && chown -R springuser:spring /app

# 复制可执行 JAR
COPY ${JAR_FILE} /app/app.jar

# 切换到非特权用户
USER springuser

# 暴露端口 (可通过 docker-compose 覆盖或映射)
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
