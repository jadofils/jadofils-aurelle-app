# syntax=docker/dockerfile:1
# Aurelle backend image. Build from the repository root, because the GraphQL schema is packaged
# from contracts/graphql (single source of truth):
#   docker build -f backend/Dockerfile -t aurelle-backend .
# Runtime config: AURELLE_DB_URL, AURELLE_DB_USER, AURELLE_DB_PASSWORD (see backend/README.md).

# ---- build ---------------------------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jdk-alpine AS build
WORKDIR /workspace/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline
COPY contracts/graphql/ /workspace/contracts/graphql/
COPY backend/src/ src/
RUN ./mvnw -B -ntp -q -DskipTests package \
 && java -Djarmode=tools -jar target/aurelle-backend-*.jar \
      extract --layers --launcher --destination /workspace/extracted

# ---- runtime -------------------------------------------------------------------------------
FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S -g 10001 aurelle && adduser -S -u 10001 -G aurelle -h /app aurelle
WORKDIR /app
COPY --from=build --chown=aurelle:aurelle /workspace/extracted/dependencies/ ./
COPY --from=build --chown=aurelle:aurelle /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=aurelle:aurelle /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=aurelle:aurelle /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
