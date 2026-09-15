# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# Compila la app React/Vite en modo producción.
# Las variables VITE_* se pasan como ARG en tiempo de build y se exponen
# como ENV para que Vite las detecte e incruste en el bundle estático.
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Instalar dependencias primero para aprovechar la caché de capas:
# si package.json no cambia, npm ci no vuelve a descargar.
COPY package.json package-lock.json ./
RUN npm ci --silent

# Variables de build requeridas por Vite.
# Se pasan en tiempo de docker build con --build-arg.
# Vite las incluye en el bundle en tiempo de compilación (no en runtime).
ARG VITE_BFF_URL
ARG VITE_AZURE_AD_CLIENT_ID
ARG VITE_AZURE_AD_TENANT_ID
ARG VITE_API_CLIENT_ID

ENV VITE_BFF_URL=$VITE_BFF_URL
ENV VITE_AZURE_AD_CLIENT_ID=$VITE_AZURE_AD_CLIENT_ID
ENV VITE_AZURE_AD_TENANT_ID=$VITE_AZURE_AD_TENANT_ID
ENV VITE_API_CLIENT_ID=$VITE_API_CLIENT_ID

# Copiar el resto del código y compilar.
COPY . .
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# Imagen Nginx mínima que sirve los estáticos generados por Vite.
# El nginx.conf incluye el fallback a index.html necesario para react-router.
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:alpine AS runtime

# Reemplazar la configuración por defecto de Nginx con la del proyecto.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copiar el bundle estático desde el stage de build.
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# nginx se ejecuta en primer plano (daemon off) para que Docker gestione
# el proceso correctamente como PID 1.
CMD ["nginx", "-g", "daemon off;"]
