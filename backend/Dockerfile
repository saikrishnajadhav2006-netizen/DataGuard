FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv ca-certificates \
    && python3 -m venv /opt/semgrep \
    && /opt/semgrep/bin/pip install --no-cache-dir semgrep tree-sitter-language-pack==0.13.0 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --uid 10001 --create-home dataguard \
    && mkdir -p /app /var/data/dataguard \
    && chown -R dataguard:dataguard /app /var/data/dataguard

WORKDIR /app
COPY --from=build /workspace/target/dataguard-backend-0.0.1-SNAPSHOT.jar /app/app.jar
ENV DATAGUARD_STORAGE_DIR=/var/data/dataguard
ENV DATAGUARD_PYTHON_EXECUTABLE=/opt/semgrep/bin/python
ENV SEMGREP_ENABLED=true
ENV SEMGREP_EXECUTABLE=/opt/semgrep/bin/semgrep
ENV SEMGREP_CONFIG=p/security-audit
ENV SEMGREP_TIMEOUT_SECONDS=90
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
