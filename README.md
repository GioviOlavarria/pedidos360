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
- Si el envío del correo falla, se reintenta 4 veces y luego el mensaje queda en `pedidos360.notifications.boleta.dlq`.
- Por defecto los correos van a **MailHog**: ábrelos en `http://localhost:8025`. Para enviar correos reales, configura las variables `MAIL_*` en `infra/.env` (ver `infra/.env.example`).
- Panel de RabbitMQ: `http://localhost:15672` (usuario/clave `RABBITMQ_USER` / `RABBITMQ_PASSWORD`).

## Kafka y RabbitMQ en Docker

Desde `infra` puedes elegir qué servicios del mismo `docker-compose.yml` levantar.

| Componente | Comando |
| --- | --- |
| RabbitMQ, tres nodos | `docker compose up -d --wait rabbitmq1 rabbitmq2 rabbitmq3` |
| Kafka, ZooKeeper, dos brokers y API de administración | `docker compose up -d --build --wait kafka-admin` |
| Kafka UI con el clúster de Kafka | `docker compose up -d --wait kafka-ui` |

Para comprobar los nodos de RabbitMQ, ejecuta `docker compose exec rabbitmq1 rabbitmqctl cluster_status`. Kafka UI permite inspeccionar los brokers, consultar grupos de consumidores y gestionar tópicos y configuraciones desde el clúster `pedidos360-cluster`.

Los clientes locales de Kafka se conectan a `localhost:9092,localhost:9093`. Desde los contenedores se usa `kafka:29092,kafka2:29092`. Para habilitar el flujo de Kafka al ejecutar `orders` o `notifications` fuera de Docker, configura `KAFKA_ENABLED=true` y, si corresponde, `KAFKA_BOOTSTRAP_SERVERS`.

### Eventos de pedidos y boletas

| Tópico | Productor | Consumidor |
| --- | --- | --- |
| `pedidos360.orders.created` | `orders`, después de confirmar el pedido en la base de datos | `notifications`, grupo `pedidos360-notifications` |
| `pedidos360.notifications.receipt-sent` | `notifications`, después de enviar la boleta por SMTP | `orders`, grupo `pedidos360-orders` |

Los eventos usan el ID del pedido como clave y una marca de tiempo ISO 8601 como valor. Los consumidores registran los eventos para trazabilidad y RabbitMQ entrega los mensajes que disparan el envío de boletas. Las publicaciones de trazabilidad se reintentan dentro del plazo del productor Kafka. Si se agota ese plazo, el microservicio registra el fallo.

El servicio `kafka-init` crea los dos tópicos de la aplicación con tres particiones y dos réplicas. Para comprobar el flujo con la plataforma completa, crea un pedido desde el frontend y revisa ambos tópicos en Kafka UI, los logs de `orders` y `notifications`, y la boleta en MailHog.

### API de administración de Kafka

El servicio `ms-pedidos360-kafka-admin` (antes `demo`) expone estas rutas en `http://localhost:8084`.

| Método y ruta | Operación |
| --- | --- |
| `GET /api/topics` | Lista tópicos |
| `POST /api/topics` | Crea un tópico con `name` y los campos opcionales `partitions`, `replicationFactor` y `configs` |
| `GET /api/topics/{topic}` | Consulta particiones, líderes y réplicas |
| `PATCH /api/topics/{topic}` | Aumenta el total de particiones, por ejemplo con `{"partitions":4}` |
| `DELETE /api/topics/{topic}` | Elimina un tópico |
| `GET /api/topics/{topic}/configs` | Consulta configuraciones efectivas del tópico |
| `PATCH /api/topics/{topic}/configs` | Agrega, actualiza o elimina configuraciones del tópico |
| `GET /api/brokers` | Lista brokers activos |
| `GET /api/brokers/{id}/configs` | Consulta configuraciones efectivas del broker |
| `PATCH /api/brokers/{id}/configs` | Agrega, actualiza o elimina configuraciones dinámicas del broker |

En un `PATCH` de configuraciones, envía un objeto JSON con valores de texto para aplicar cambios, por ejemplo `{"retention.ms":"86400000"}`. Envía un valor `null` para quitar el valor personalizado y volver al valor heredado. Kafka admite aumentar el número de particiones y cambiar las configuraciones que el broker permite modificar en caliente.

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

### Infraestructura
* Docker, Docker Compose
* GitHub Actions (CI/CD)
