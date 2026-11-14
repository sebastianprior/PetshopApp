# Frontend de Petshop: se compila con Vite y se sirve con nginx.

# --- 1) Compilar la tienda --------------------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
# La dirección de la API se incorpora al compilar (Vite la lee en tiempo de build)
ARG VITE_API_URL=http://localhost:8080/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# --- 2) Servir los archivos estáticos ----------------------------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine
# nginx procesa los archivos de templates/ al arrancar (reemplaza ${API_ORIGIN}) y los deja en conf.d/
ENV API_ORIGIN=http://localhost:8080
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.inc.template /etc/nginx/templates/security-headers.inc.template
COPY --from=build /build/dist /usr/share/nginx/html
# Sin root: esta imagen de nginx corre con un usuario sin privilegios y por eso escucha en el 8080 (no en el 80)
EXPOSE 8080
