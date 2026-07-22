ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${RUNTIME_IMAGE}

ARG JAR_FILE
ARG SERVICE_NAME=zhyf-service

ENV TZ=Asia/Hong_Kong \
    JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=default

WORKDIR /app

RUN apk add --no-cache curl tzdata \
    && addgroup -S zhyf \
    && adduser -S zhyf -G zhyf

COPY ${JAR_FILE} /app/app.jar

RUN chown -R zhyf:zhyf /app

USER zhyf

EXPOSE 8080

LABEL org.opencontainers.image.title="${SERVICE_NAME}"

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
