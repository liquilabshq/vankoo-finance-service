# Etapa 1: Compilación
FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app

# 1. Copiamos el POM y descargamos dependencias (Caché de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copiamos el código fuente y generamos el JAR
# Solo pom.xml y src entran a la imagen. config/application-local.yaml (secretos
# reales, ignorado por git) se queda fuera a propósito; ver también .dockerignore.
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Runtime (Imagen ligera)
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Instalamos curl para que el healthcheck de Docker funcione
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Usuario de seguridad para no correr como root
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring

# Copiamos el JAR (el artifactId del pom es 'finance': target/finance-0.0.1-SNAPSHOT.jar)
COPY --from=build /app/target/finance-*.jar app.jar

# Exponemos el puerto que configuramos en application-docker.yaml
EXPOSE 8083

# Ejecutamos con el perfil de docker activo. Las claves de Stripe y el resto de
# variables ${...} las inyecta el docker-compose por environment:, no la imagen.
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
