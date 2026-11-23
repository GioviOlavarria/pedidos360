# Pedidos360

![Java](https://img.shields.io/badge/Java-21-orange?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=spring-boot)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)
![Azure AD](https://img.shields.io/badge/Azure_Active_Directory-0078D4?logo=microsoft-azure)

Plataforma integral de gestión de pedidos y catálogo para e-commerce. Desarrollada con arquitectura de microservicios usando **Spring Boot (WebFlux / MVC)**, **React** y protegida corporativamente a través de **Microsoft Entra ID (Azure AD)**.

## Características
- **Arquitectura BFF (Backend For Frontend)**: Patrón que canaliza las llamadas del cliente de forma reactiva (Spring WebFlux).
- **Catálogo y Órdenes Independientes**: Microservicios separados para la gestión de productos e inventario, y para el flujo completo de compras.
- **Autenticación Corporativa**: Integración nativa con Azure AD mediante MSAL-Browser y Spring Security OAuth2 (Roles: `Admin`, `Operator`, `Customer`).
- **Diseño Moderno (UI/UX)**: Interfaz de usuario optimizada inspirada en paneles de control logísticos de primer nivel, responsive y rápida (Vite + React).
- **Despliegue unificado con Docker**: Microservicios Java, frontend React/Nginx, MySQL, RabbitMQ y Kafka orquestados con `docker compose`.
- **Integración Continua (CI/CD)**: Flujos automatizados con GitHub Actions para asegurar compilaciones limpias.

---

## Arquitectura del Sistema

```text
                     [Nginx]
[Navegador]  <---->  Frontend (React SPA)  (Puerto 3000)
    | (JWT)
    v
[BFF Gateway] <--->  ms-pedidos360-bff (WebFlux) (Puerto 8080)
    |
    +-----> [API] ms-pedidos360-catalog (Puerto 8082) ---> [BD MySQL]
    |
    +-----> [API] ms-pedidos360-orders  (Puerto 8081) ---> [BD MySQL]

Mensajería
  RabbitMQ (3 nodos, colas quorum + DLQ)  orders --order.created--> notifications (boleta por correo)
  Kafka (ZooKeeper + 2 brokers)           orders --pedidos360.orders.events--> catalog, notifications
                                          notifications --pedidos360.billing.receipts--> orders
Administración
  ms-pedidos360-kafka-admin  (8084)  tópicos, configuraciones, brokers y consumer groups
  ms-pedidos360-rabbit-admin (8086)  colas, exchanges y bindings
  Kafka UI (8085) · RabbitMQ Management (15672) · MailHog (8025)
```

---

## Requisitos Previos

Si vas a levantar el proyecto en tu entorno local o en otro equipo, sólo necesitas:
- [Docker](https://docs.docker.com/get-docker/) y Docker Compose instalados.
- (Opcional) Git para clonar el repositorio.
- No se requiere instalar Java, Maven o Node.js en tu máquina, ya que los `Dockerfile` son multi-etapa (multi-stage) y compilan el proyecto aisladamente.

---

## Instrucciones de Ejecución

1. **Clona el repositorio** (Si aún no lo tienes localmente):
   ```bash
   git clone https://github.com/GioviOlavarria/pedidos360
   cd pedidos360
   ```

2. **Configura las variables de entorno**:
   Ingresa a la carpeta `infra` y crea un archivo `.env` basado en la plantilla:
   ```bash
   cd infra
   cp .env.example .env
   ```
   Abre el archivo `.env` y rellena con los credenciales de Microsoft Entra ID y las credenciales que desees usar para tu base de datos MySQL virtual (ej: `RootPedidos2024!`). 
   
   *(Importante: Si ejecutas el código localmente, la variable `VITE_BFF_URL` debe ser `http://localhost:8080`)*.

3. **Inicia los servicios**:
   Estando en la carpeta `infra`, ejecuta el comando de Docker Compose:
   ```bash
   docker compose up --build -d
   ```
   Este proceso descarga las imágenes, compila el código y levanta la aplicación con MySQL, RabbitMQ, Kafka, ZooKeeper, Kafka UI y MailHog.

4. **Accede a la plataforma**:
   - Plataforma web (Frontend): `http://localhost:3000`
   - API Gateway / BFF: `http://localhost:8080`
   - Inicia sesión con tu cuenta de Microsoft.
   - Bandeja de correos de prueba (boletas): `http://localhost:8025`
   - Panel de RabbitMQ: `http://localhost:15672`
   - Panel de Kafka UI: `http://localhost:8085`
   - API de administración de Kafka: `http://localhost:8084/api/topics`
   - API de administración de RabbitMQ: `http://localhost:8086/api/queues`

---

## Roles y Permisos

Se puede iniciar sesión con **cualquier cuenta de Microsoft** (corporativa, institucional o personal):

- **Admin**: los correos definidos en `ADMIN_EMAILS` (por defecto `gi.olavarria@duocuc.cl`). Entra directo al panel de administración: catálogo, gestión de pedidos y reportes.
- **Customer**: cualquier otra cuenta. Puede comprar (compras ficticias), ver sus propios pedidos y recibe la boleta por correo.
- Sin sesión se puede ver el catálogo y hacer seguimiento por número de pedido, pero **no comprar**.

El rol lo decide el **backend** a partir del token (claim `preferred_username`/`email`). El frontend solo lo usa para mostrar las pantallas, así que no se puede "hacerse admin" desde el navegador. También se respetan los App Roles `Admin`/`Operator` de Azure si se asignan.

### Configuración requerida en Azure (Entra ID)

1. **App Registration de la API** (`API_CLIENT_ID`):
   - *Supported account types*: "Accounts in any organizational directory and personal Microsoft accounts".
   - *Expose an API*: scope `access_as_user`, y agregar el client id del frontend como *Authorized client application*.
   - *Manifest*: `"requestedAccessTokenVersion": 2` (obligatorio para cuentas personales).
   - *Token configuration*: agregar los optional claims `email` y `preferred_username` al **access token** (el backend los usa para saber quién compra, a qué correo enviar la boleta y si es Admin).
2. **App Registration del frontend** (`AZURE_AD_CLIENT_ID`): mismo tipo de cuentas, plataforma *SPA* con redirect URI `http://localhost:3000/auth/callback` (y `http://localhost:5173/auth/callback` para desarrollo con Vite), y permiso delegado al scope `access_as_user` de la API.

El backend valida que cada token haya sido emitido **para esta API** (`aud`) por el tenant indicado en el propio token (`tid`/`iss`).

## Flujo de compra y boleta (RabbitMQ)

```text
Cliente compra ──> BFF ──> orders ──(reserva stock, precios reales)──> catalog
                             │
                             └─ tras guardar el pedido publica "order.created"
                                         │
                        RabbitMQ (cluster 3 nodos, cola quorum)
                                         │
                                  notifications ──> correo con la boleta (SMTP)
```

- El total y los precios se calculan en el servidor con los datos del catálogo; el stock se descuenta de forma atómica.
- Antes de enviar el correo, notifications emite la **boleta electrónica (DTE 39)** a través del puerto `SiiClient` (ver más abajo). El correo incluye folio, neto, IVA (19 %), timbre y track ID.
- Si el envío del correo falla, se reintenta 4 veces y luego el mensaje queda en `pedidos360.notifications.boleta.dlq`.
- Por defecto los correos van a **MailHog**: ábrelos en `http://localhost:8025`. Para enviar correos reales, configura las variables `MAIL_*` en `infra/.env` (ver `infra/.env.example`).
- Panel de RabbitMQ: `http://localhost:15672` (usuario/clave `RABBITMQ_USER` / `RABBITMQ_PASSWORD`).

## Kafka: streaming de eventos

Kafka **complementa** a RabbitMQ sin reemplazarlo: RabbitMQ sigue siendo la cola de trabajo de la boleta (un mensaje, un consumidor, DLQ), mientras Kafka guarda el **historial de eventos** del pedido para que varios servicios lo lean de forma independiente (analítica y reacciones), con retención y posibilidad de reprocesar.

### Tópicos

| Tópico | Productor | Consumer groups | Particiones / réplicas | Configuración |
| --- | --- | --- | --- | --- |
| `pedidos360.orders.events` | `orders` (CREATED, UPDATED, STATUS_CHANGED, DELETED) | `pedidos360-catalog-sales` (catalog), `pedidos360-notifications` (notifications) | 3 / 2 | `cleanup.policy=delete`, `retention.ms` = 7 días, `min.insync.replicas=1` |
| `pedidos360.billing.receipts` | `notifications` (boleta emitida) | `pedidos360-orders` (orders) | 3 / 2 | `cleanup.policy=compact` (última boleta por pedido), `min.compaction.lag.ms` = 1 min, `segment.ms` = 1 h |
| `*.DLT` | error handler de cada consumidor | revisión manual | 1 / 2 | `cleanup.policy=delete`, `retention.ms` = 30 días |

- **Clave = id del pedido**: todos los eventos de un pedido van a la misma partición y se consumen en orden.
- **DTO JSON** (`OrderEventMessage`, `ReceiptIssuedMessage`) con `JsonSerializer` y `ErrorHandlingDeserializer` + `JsonDeserializer`.
- **Productores**: `acks=all`, idempotencia, compresión lz4 y publicación después del commit (`@TransactionalEventListener(AFTER_COMMIT)`).
- **Consumer groups**: catalog y notifications leen el mismo tópico con grupos distintos (cada uno recibe todo y cumple su responsabilidad). Dentro de cada grupo, 3 hilos (`concurrency=3`) se reparten las 3 particiones con `CooperativeStickyAssignor`; si se levantan más instancias, Kafka rebalancea las particiones entre ellas.
- **Manejo de errores**: validación del payload (Bean Validation y clave = orderId) y `DefaultErrorHandler` con 3 reintentos y backoff exponencial (1 s, 2 s, 4 s); luego el registro va a `<tópico>.DLT` con los headers de la excepción. Los payloads inválidos o no deserializables van directo al DLT sin bloquear la partición. Los logs incluyen tópico, partición, offset y clave.
- **Lógica separada de la mensajería**: los `@KafkaListener` solo validan y delegan (`OrderService.registerReceipt`, `SalesProjectionService`, `OrderStatusMailService`).
- Los tópicos se crean con `infra/kafka/create-topics.sh` (servicio `kafka-init`) y además cada servicio los declara como `NewTopic` (`KafkaAdmin` con `modify-topic-configs`).

Qué hace cada consumidor:
- **catalog** proyecta las ventas por producto de los pedidos vigentes (`GET /api/catalog/sales-stats`, solo Admin; se ve en *Reportes*). Es idempotente: cada evento reemplaza las líneas de su pedido.
- **notifications** avisa por correo al cliente cada cambio de estado del pedido.
- **orders** guarda en el pedido el folio, el track ID y el estado SII de la boleta (se ve en el detalle del pedido).

### Levantar solo Kafka o solo RabbitMQ

| Componente | Comando (desde `infra`) |
| --- | --- |
| RabbitMQ (3 nodos) y su API admin | `docker compose up -d --build rabbitmq1 rabbitmq2 rabbitmq3 rabbit-admin` |
| ZooKeeper, 2 brokers, tópicos y API admin | `docker compose up -d --build kafka-admin` |
| Kafka UI | `docker compose up -d kafka-ui` |

Clientes en el host: `localhost:9092,localhost:9093`. Entre contenedores: `kafka:29092,kafka2:29092`. Para usar Kafka con `orders`, `catalog` o `notifications` fuera de Docker, define `KAFKA_ENABLED=true`.

### API de administración de Kafka (`http://localhost:8084`)

| Método y ruta | Operación |
| --- | --- |
| `GET /api/topics` | Lista tópicos |
| `POST /api/topics` | Crea un tópico (`name`, `partitions`, `replicationFactor`, `configs`) |
| `GET /api/topics/{topic}` | Particiones, líderes, réplicas e ISR |
| `PATCH /api/topics/{topic}` | Aumenta particiones (`{"partitions":4}`) |
| `DELETE /api/topics/{topic}` | Elimina un tópico |
| `GET`, `PATCH /api/topics/{topic}/configs` | Consulta o modifica configuraciones (`{"retention.ms":"86400000"}`; `null` vuelve al valor heredado) |
| `GET /api/brokers` | Lista brokers |
| `GET`, `PATCH /api/brokers/{id}/configs` | Consulta o modifica configuraciones dinámicas del broker |
| `GET /api/consumer-groups` | Lista consumer groups y su estado |
| `GET /api/consumer-groups/{id}` | Miembros, asignación de particiones, offsets y lag |
| `POST /api/consumer-groups/{id}/reset-offsets` | Reinicia offsets (`{"topic":"...","to":"earliest"}` o `"latest"`; el grupo debe estar detenido) |
| `DELETE /api/consumer-groups/{id}` | Elimina un grupo sin consumidores |

### API de administración de RabbitMQ (`http://localhost:8086`)

| Método y ruta | Operación |
| --- | --- |
| `GET`, `POST /api/queues` | Lista o crea colas (`name`, `type` classic o quorum, `deadLetterExchange`, `deadLetterRoutingKey`) |
| `DELETE /api/queues/{name}` | Elimina una cola (`DELETE /api/queues/{name}/messages` la purga) |
| `GET`, `POST /api/exchanges` | Lista o crea exchanges (`name`, `type` direct, topic, fanout o headers) |
| `DELETE /api/exchanges/{name}` | Elimina un exchange |
| `GET`, `POST /api/bindings` | Lista o crea bindings (`exchange`, `queue`, `routingKey`) |
| `DELETE /api/bindings?exchange=...&queue=...&routingKey=...` | Elimina un binding |

## Boleta electrónica (SII)

`SiiClient` (en `ms-pedidos360-notifications`, paquete `sii`) es el puerto hacia el Servicio de Impuestos Internos. La implementación incluida, `SimulatedSiiClient` (`SII_MODE=simulado`), aplica las reglas de la boleta afecta (DTE 39): precios con IVA incluido, neto = total / 1,19, IVA = total − neto y receptor consumidor final `66666666-6`. Genera folio, timbre y track ID deterministas, así que reintentar entrega la misma boleta.

La API REST real del SII (`apicert.sii.cl` en certificación y `api.sii.cl` en producción, con el flujo semilla → token → envío → consulta por track ID) exige el **certificado digital** del contribuyente, **folios CAF** autorizados y la **certificación** de la empresa ante el SII. Para conectarla basta con agregar otra implementación de `SiiClient` y activarla con `SII_MODE`.

## Tecnologías Utilizadas

### Frontend
* React 19, React Router DOM v7
* Vite 8, Axios
* @azure/msal-browser, @azure/msal-react (Auth)
* Nginx (Web server en Docker)

### Backend
* Java 21, Spring Boot 3.3.5
* Spring WebFlux (BFF), Spring Web MVC (Microservicios)
* Spring Security (OAuth2 Resource Server)
* Spring Data JPA, Hibernate, MySQL Driver

### Mensajería
* RabbitMQ 3.13 (clúster de 3 nodos, colas quorum, DLQ), Spring AMQP
* Apache Kafka (Confluent 7.9, ZooKeeper + 2 brokers), Spring for Apache Kafka, Kafka UI

### Infraestructura
* Docker, Docker Compose
* GitHub Actions (CI/CD)
