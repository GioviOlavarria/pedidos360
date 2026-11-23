# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build
# Compila la app Angular en modo producción. Los identificadores de Azure AD y la URL del BFF
# llegan como build args; scripts/set-env.mjs los escribe en src/environments/environment.ts
# (npm run build ejecuta antes "prebuild") y quedan incrustados en el bundle estático.
# ─────────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app

# Instalar dependencias primero para aprovechar la caché de capas.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

ARG BFF_URL
ARG AZURE_AD_CLIENT_ID
ARG AZURE_AD_TENANT_ID
ARG API_CLIENT_ID
ARG ADMIN_EMAILS

ENV BFF_URL=$BFF_URL \
    AZURE_AD_CLIENT_ID=$AZURE_AD_CLIENT_ID \
    AZURE_AD_TENANT_ID=$AZURE_AD_TENANT_ID \
    API_CLIENT_ID=$API_CLIENT_ID \
    ADMIN_EMAILS=$ADMIN_EMAILS

COPY . .
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — runtime
# Nginx sirve los estáticos; nginx.conf incluye el fallback a index.html que necesita el router de Angular.
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend-pedidos360/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
