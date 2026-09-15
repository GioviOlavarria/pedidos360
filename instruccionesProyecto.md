CONTEXTO FIJO DEL PROYECTO — PEDIDOS360 (no cambiar estos nombres bajo ninguna circunstancia)

Sistema: Pedidos360
Etapa actual: Evaluación Parcial 1 — arquitectura base + autenticación (NO incluir Kafka, RabbitMQ, Docker ni microservicios de reportería/auditoría, eso es de etapas futuras)

REPOSITORIOS Y COMPONENTES:
- frontend-pedidos360 → React + JavaScript (NO TypeScript, NO Angular)
- ms-pedidos360-bff → Spring Boot 3.3+, Java 21, Maven, Group: com.pedidos360, Artifact: bff, Package: com.pedidos360.bff
- ms-pedidos360-orders → Spring Boot 3.3+, Java 21, Maven, Group: com.pedidos360, Artifact: orders, Package: com.pedidos360.orders
- ms-pedidos360-catalog → Spring Boot 3.3+, Java 21, Maven, Group: com.pedidos360, Artifact: catalog, Package: com.pedidos360.catalog

PUERTOS FIJOS:
- frontend-pedidos360: 3000
- ms-pedidos360-bff: 8080
- ms-pedidos360-orders: 8081
- ms-pedidos360-catalog: 8082
- MySQL local: 3306

BASE DE DATOS: MySQL, esquema pedidos360_db

ENTIDADES:
- Order (id, customerId, status, createdAt, total) — orders
- OrderItem (id, orderId, productId, quantity, unitPrice) — orders
- Estados válidos y orden estricto: CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO, o CANCELADO en cualquier punto antes de ENTREGADO. Regla dura: no se puede pasar a DESPACHADO sin haber pasado por ACEPTADO.
- Product (id, name, description, price, stock) — catalog

ROLES AZURE AD (exactamente estos 3, mismo texto): Admin, Operator, Customer

VARIABLES DE ENTORNO FIJAS:
- AZURE_AD_TENANT_ID
- AZURE_AD_CLIENT_ID
- API_CLIENT_ID
- JWT_ISSUER_URI
- DB_URL, DB_USER, DB_PASSWORD

Responde SOLO sobre el alcance pedido en el prompt puntual. No agregues funcionalidad de otros componentes ni de otras etapas. No propongas nombres alternativos a los ya definidos aquí.

- Cada microservicio (bff, orders, catalog) usa Dockerfile multi-stage: build con maven:3.9-eclipse-temurin-21 (o similar), runtime con eclipse-temurin:21-jre-alpine.
- El frontend usa Dockerfile multi-stage: build con node:20-alpine (npm run build), runtime servido con nginx:alpine.
- Nombres de servicio en docker-compose.yml (usados como hostname interno, reemplazan localhost entre contenedores):
    - mysql (puerto interno 3306)
    - orders (puerto interno 8081)
    - catalog (puerto interno 8082)
    - bff (puerto interno 8080)
    - frontend (puerto interno 80, expuesto en 3000)
- Todas las variables sensibles (AZURE_AD_*, DB_*) se centralizan en un archivo .env en la raíz de /infra, referenciado por docker-compose.yml.
- El frontend en modo producción necesita las variables VITE_* inyectadas EN TIEMPO DE BUILD (no en runtime), así que el Dockerfile debe recibirlas como ARG/build-args desde docker-compose.