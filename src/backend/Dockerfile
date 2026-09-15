# syntax=docker/dockerfile:1
FROM maven:3.9.16-eclipse-temurin-21@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
# Let ./mvnw reuse the exact Maven distribution already present in the digest-pinned builder.
RUN mkdir -p /root/.m2/wrapper/dists/apache-maven-3.9.16 \
    && ln -s /usr/share/maven /root/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2/repository ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/aims-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=production PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
