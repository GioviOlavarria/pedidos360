# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=build /app/target/catalog-0.0.1-SNAPSHOT.jar app.jar

# ── Puerto expuesto: ms-pedidos360-catalog ───────────────────────────────────
# LÍNEA A CAMBIAR por microservicio:
#   ms-pedidos360-bff     → EXPOSE 8080
#   ms-pedidos360-orders  → EXPOSE 8081
#   ms-pedidos360-catalog → EXPOSE 8082  (este archivo)
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
