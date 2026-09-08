# Build and verify with Java21/Maven Wrapper before building this runtime image.
FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699
WORKDIR /app
COPY --chown=10001:10001 target/aims-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=production PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
