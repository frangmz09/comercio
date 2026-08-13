# --- build stage: compila el monorepo con la cache de dependencias de Maven ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Se copian los pom de todos los modulos, no solo los que se compilan: el reactor de
# Maven lee el pom padre entero y falla si falta el descriptor de alguno de sus modulos,
# aunque despues no se construya.
COPY pom.xml .
COPY contratos/pom.xml contratos/pom.xml
COPY core/pom.xml core/pom.xml
COPY reportes/pom.xml reportes/pom.xml
RUN mvn -q -pl reportes -am -DskipTests dependency:go-offline

COPY contratos/src contratos/src
COPY reportes/src reportes/src
RUN mvn -q -pl reportes -am -DskipTests package

# --- run stage: solo el jar y un JRE, sin toolchain de build ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S comercio && adduser -S comercio -G comercio
COPY --from=build /workspace/reportes/target/reportes.jar app.jar
USER comercio

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
