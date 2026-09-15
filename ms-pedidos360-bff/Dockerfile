# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# Compila el proyecto con Maven y genera el JAR ejecutable.
# Se usa la imagen oficial de Maven con Temurin 21 para reproducibilidad.
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copiar pom.xml primero para aprovechar la caché de capas de Docker:
# si el pom no cambia, Maven no vuelve a descargar dependencias.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copiar el código fuente y empaquetar (sin tests para acelerar el build).
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# Imagen mínima JRE Alpine (~85 MB vs ~500 MB del JDK completo).
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Copiar únicamente el JAR desde el stage de build.
COPY --from=build /app/target/bff-0.0.1-SNAPSHOT.jar app.jar

# ── Puerto expuesto: ms-pedidos360-bff ───────────────────────────────────────
# LÍNEA A CAMBIAR por microservicio:
#   ms-pedidos360-bff     → EXPOSE 8080  (este archivo)
#   ms-pedidos360-orders  → EXPOSE 8081
#   ms-pedidos360-catalog → EXPOSE 8082
EXPOSE 8080

# Ejecutar el JAR. Se usa la forma exec (array JSON) para que Java reciba
# las señales del SO directamente (PID 1 correcto, shutdown limpio).
ENTRYPOINT ["java", "-jar", "app.jar"]
