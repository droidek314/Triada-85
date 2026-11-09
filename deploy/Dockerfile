# The TRIADA-NET server as a container image (M18). Build it from the
# repository's top folder:
#
#   docker build -f deploy/Dockerfile -t triada-net .
#
# Stage 1 compiles the server with the project's own Gradle wrapper; stage 2
# keeps only the result and a Java runtime. All settings are environment
# variables (see deploy/.env.example and docs/net/DEPLOY.md).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :triada-net-server:installDist -x test

FROM eclipse-temurin:25-jre
# Run as a plain user, never as root; the database lives in /data (a volume).
RUN useradd --system --home-dir /data --shell /usr/sbin/nologin triada \
    && mkdir -p /data && chown triada /data
COPY --from=build /src/triada-net-server/build/install/triada-net-server /app
USER triada
WORKDIR /data
ENV TRIADA_NET_DB=/data/triada-net.db
# 2385: TCP for desktop emulators. 2386: WebSocket (and /health, /admin), meant to sit behind Caddy.
EXPOSE 2385 2386
ENTRYPOINT ["/app/bin/triada-net-server"]
