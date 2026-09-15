# Pedidos360

![Java](https://img.shields.io/badge/Java-21-orange?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=spring-boot)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?logo=vite)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)
![Azure AD](https://img.shields.io/badge/Azure_Active_Directory-0078D4?logo=microsoft-azure)

Plataforma integral de gestión de pedidos y catálogo para e-commerce. Desarrollada con arquitectura de microservicios usando **Spring Boot (WebFlux / MVC)**, **React** y protegida corporativamente a través de **Microsoft Entra ID (Azure AD)**.

## Características
- **Arquitectura BFF (Backend For Frontend)**: Patrón que canaliza las llamadas del cliente de forma reactiva (Spring WebFlux).
- **Catálogo y Órdenes Independientes**: Microservicios separados para la gestión de productos e inventario, y para el flujo completo de compras.
- **Autenticación Corporativa**: Integración nativa con Azure AD mediante MSAL-Browser y Spring Security OAuth2 (Roles: `Admin`, `Operator`, `Customer`).
- **Diseño Moderno (UI/UX)**: Interfaz de usuario optimizada inspirada en paneles de control logísticos de primer nivel, responsive y rápida (Vite + React).
- **Despliegue unificado con Docker**: Todo el ecosistema (MySQL, 3 Microservicios Java, Frontend React/Nginx) orquestado con `docker compose`.
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
   Este proceso descargará las imágenes base, compilará el código fuente (Java y React) en paralelo, y levantará 5 contenedores en la red `pedidos360-net`.

4. **Accede a la plataforma**:
   - Plataforma web (Frontend): `http://localhost:3000`
   - Inicia sesión con tu cuenta de Microsoft.

---

## Roles y Permisos (Azure AD)
La aplicación maneja 3 roles corporativos que deben ser definidos en el registro de tu aplicación en **Microsoft Entra ID**:
- **Admin**: Acceso total. Visualiza reportes financieros exclusivos, gestiona catálogo completo, y modifica/crea órdenes.
- **Operator**: Puede visualizar, cambiar estados de pedidos y ver/modificar catálogo de productos.
- **Customer**: Sólo puede visualizar/crear pedidos.

> Para asignar un rol a un usuario, hazlo desde *Azure Portal > Entra ID > Aplicaciones Empresariales > [Tu App Frontend] > Usuarios y Grupos*.

## Tecnologías Utilizadas

### Frontend
* React 18, React Router DOM v7
* Vite 5, Axios
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
