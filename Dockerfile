# syntax=docker/dockerfile:1

############################
# Build stage
############################
FROM eclipse-temurin:21-jre-jammy

# ===== 代理（构建阶段用：maven 拉依赖）=====
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG http_proxy
ARG https_proxy
ENV HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    http_proxy=${http_proxy} \
    NO_PROXY="localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12,::1,localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12,172.29.0.0/16,::1" \
    https_proxy=${https_proxy}

WORKDIR /app

ARG APP_JAR_NAME
# 再拷贝源码并打包
COPY ${APP_JAR_NAME} /app/app.jar

RUN mkdir -p /app/logs
# 与 config.port 一致
EXPOSE 8000

# CMD ["tail", "-f", "/dev/null"]

ENV http_proxy="" \
 https_proxy="" \
 all_proxy="" \
 HTTP_PROXY="" \
 HTTPS_PROXY="" \
 ALL_PROXY=""

ENV JAVA_OPTS=" -Duser.timezone=GMT+08 "
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]