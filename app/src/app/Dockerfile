# Frontend de Petshop: se compila con Vite y se sirve con nginx.

# --- 1) Compilar la tienda --------------------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# La dirección de la API se incorpora al compilar (Vite la lee en tiempo de build)
ARG VITE_API_URL=http://localhost:8080/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# --- 2) Servir los archivos estáticos ----------------------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
