# Backend de Petshop para publicarlo (Render, Railway, etc.). El frontend no entra en esta imagen: va en Vercel.

# --- 1) Compilar el .jar ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Primero lo que cambia poco (Maven y dependencias) para aprovechar la caché de capas
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/main src/main
RUN ./mvnw -q -B -DskipTests package && cp target/app-*.jar /build/app.jar

# --- 2) Imagen final, liviana ----------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/app.jar app.jar

# Carpetas donde la app guarda archivos (imágenes subidas por el admin y emails simulados)
RUN mkdir -p uploads outbox logs artemis-data

# Memoria acotada: los planes gratuitos tienen ~512 MB
ENV ARTEMIS_PERSISTENT=true
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k"

# El puerto lo define la plataforma con PORT (por defecto, 8080)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
