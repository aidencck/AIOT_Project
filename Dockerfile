# AIoT 微服务通用 Dockerfile

FROM eclipse-temurin:17-jre-alpine

# 定义构建参数
ARG JAR_FILE=target/*.jar
ARG APP_NAME="app.jar"

# 环境变量设置默认值（可在 docker-compose 中覆盖）
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom" \
    NACOS_ADDR="nacos:8848" \
    MYSQL_HOST="mysql:3306" \
    REDIS_HOST="redis"

# 设置工作目录
WORKDIR /app

# 设置时区
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 复制编译后的 jar 包
COPY ${JAR_FILE} ${APP_NAME}

# 暴露端口由 docker-compose 控制
EXPOSE 8080

# 启动命令
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar ${APP_NAME}"]
