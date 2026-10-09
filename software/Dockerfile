# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=25
ARG OTEL_AGENT_VERSION=2.31.1

FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

RUN mv build/libs/*-SNAPSHOT.jar /workspace/aplicacao.jar


FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS preparo

ARG OTEL_AGENT_VERSION

WORKDIR /preparo

COPY --from=build /workspace/aplicacao.jar ./aplicacao.jar

RUN java -Djarmode=tools -jar aplicacao.jar extract --layers --launcher --destination extraido

RUN apk add --no-cache curl && \
    curl -fsSL --retry 5 --retry-all-errors --retry-delay 3 \
      -o /preparo/opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" && \
    test -s /preparo/opentelemetry-javaagent.jar

RUN mkdir -p /preparo/execucao \
    && cp -rl /preparo/extraido/dependencies/. /preparo/execucao/ \
    && cp -rl /preparo/extraido/spring-boot-loader/. /preparo/execucao/ \
    && cp -rl /preparo/extraido/snapshot-dependencies/. /preparo/execucao/ \
    && cp -rl /preparo/extraido/application/. /preparo/execucao/

WORKDIR /preparo/execucao

RUN env -u OTEL_EXPORTER_OTLP_ENDPOINT \
        -u OTEL_EXPORTER_OTLP_PROTOCOL \
        -u OTEL_EXPORTER_OTLP_TRACES_ENDPOINT \
        -u OTEL_EXPORTER_OTLP_TRACES_PROTOCOL \
        -u OTEL_EXPORTER_OTLP_LOGS_ENDPOINT \
        -u OTEL_RESOURCE_ATTRIBUTES \
        -u OTEL_SERVICE_NAME \
        -u TRACEPARENT \
    java -XX:ArchiveClassesAtExit=/preparo/aplicacao.jsa \
         -Dspring.context.exit=onRefresh \
         -Dmanagement.tracing.enabled=false \
         -Dservicetrack.security.jwt.enabled=false \
         -Dspring.main.banner-mode=off \
         -Dservicetrack.usuarios.base-url=http://localhost:1 \
         -Dservicetrack.catalogo.base-url=http://localhost:1 \
         -Dservicetrack.ordens.base-url=http://localhost:1 \
         org.springframework.boot.loader.launch.JarLauncher > /preparo/treino.log 2>&1 \
    && test -s /preparo/aplicacao.jsa \
    || (echo "ERRO: o treino de CDS nao completou o refresh do contexto" \
        && grep -oE "threw exception with message: .*" /preparo/treino.log | tail -2 \
        && tail -25 /preparo/treino.log && exit 1)


FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime

RUN addgroup -S servicetrack && adduser -S servicetrack -G servicetrack

WORKDIR /app

COPY --from=preparo --chown=servicetrack:servicetrack /preparo/extraido/dependencies/ ./
COPY --from=preparo --chown=servicetrack:servicetrack /preparo/extraido/spring-boot-loader/ ./
COPY --from=preparo --chown=servicetrack:servicetrack /preparo/extraido/snapshot-dependencies/ ./
COPY --from=preparo --chown=servicetrack:servicetrack /preparo/extraido/application/ ./
COPY --from=preparo --chown=servicetrack:servicetrack --chmod=444 /preparo/aplicacao.jsa ./aplicacao.jsa
COPY --from=preparo --chown=servicetrack:servicetrack --chmod=444 /preparo/opentelemetry-javaagent.jar ./opentelemetry-javaagent.jar

USER servicetrack

ENV OTEL_JAVAAGENT_ENABLED=false
ENV OTEL_SERVICE_NAME=service-track-bff
ENV SPRING_PROFILES_ACTIVE=prod

ENV JDK_JAVA_OPTIONS="\
-XX:MaxRAMPercentage=60 \
-XX:InitialRAMPercentage=25 \
-XX:+UseSerialGC \
-Xshare:auto \
-XX:SharedArchiveFile=/app/aplicacao.jsa \
-XX:+ExitOnOutOfMemoryError \
-Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "org.springframework.boot.loader.launch.JarLauncher"]
