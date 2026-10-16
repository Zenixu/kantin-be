# ──────────────────────────────────────────
# Stage 1: Build
# ──────────────────────────────────────────
FROM azul/zulu-openjdk-alpine:25 AS builder

# ttf-dejavu + fontconfig: Apache POI (ekspor Excel) butuh font saat runtime
# (lihat #215). fontconfig ditarik transitif oleh ttf-dejavu, tapi kita nyatakan
# EKSPLISIT agar tidak bergantung pada detail dependensi Alpine.
RUN apk add --no-cache maven tzdata ttf-dejavu fontconfig
ENV TZ=Asia/Jakarta
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# Cache Maven deps — layer hanya re-build kalau pom.xml berubah
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# ──────────────────────────────────────────
# Stage 2: Runtime
# ──────────────────────────────────────────
FROM azul/zulu-openjdk-alpine:25

# ttf-dejavu + fontconfig — WAJIB untuk ekspor Excel (Apache POI). Bila font
# tidak ada, POI gagal dengan "Fontconfig head is null" → GET /api/laporan/ekspor
# balas 500 (bug #215). Dinyatakan EKSPLISIT agar tidak bergantung pada
# dependensi transitif Alpine yang bisa berubah.
RUN apk add --no-cache tzdata ttf-dejavu fontconfig curl && \
    addgroup -S appgroup && adduser -S appuser -G appgroup

ENV TZ=Asia/Jakarta
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8082

# Health check image aplikasi (issue #149) — compose punya healthcheck untuk
# infra (Postgres/Redis), tetapi image app belum. Probes actuator aktif
# (management.endpoint.health.probes.enabled=true) → /actuator/health/liveness.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health/liveness || exit 1

# Override via env deployment: JAVA_OPTS=-Xms256m -Xmx512m
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
