# SameBoy (the emulator core), its boot ROM and our JNI glue, built for the image's own platform (arm64 on the Pi:
# under QEMU on the runner): C, so it must match the CPU the jar runs on. jni.h comes from the JDK image.
FROM eclipse-temurin:21-jdk AS native
RUN apt-get update && apt-get install -y --no-install-recommends git make gcc libc6-dev rgbds ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY pppp-emulator/native /work/pppp-emulator/native
RUN JAVA_HOME=/opt/java/openjdk /work/pppp-emulator/native/build.sh

# Build on the runner's own arch: the jar is portable, only the runtime image is arm64.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY pppp-emulator/pom.xml pppp-emulator/
COPY pppp-core/pom.xml pppp-core/
COPY pppp-app/pom.xml pppp-app/
RUN mvn -q -B dependency:go-offline -DexcludeArtifactIds=pppp-emulator,pppp-core || true
COPY . .
COPY --from=native /work/pppp-emulator/src/main/resources/native pppp-emulator/src/main/resources/native
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/pppp-app/target/pppp-app-*.jar app.jar
ENV PPPP_DATA_DIR=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
