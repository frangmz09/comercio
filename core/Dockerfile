# --- build stage: compila el monorepo con la cache de dependencias de Maven ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY contratos/pom.xml contratos/pom.xml
COPY core/pom.xml core/pom.xml
RUN mvn -q -pl core -am -DskipTests dependency:go-offline

COPY contratos/src contratos/src
COPY core/src core/src
RUN mvn -q -pl core -am -DskipTests package

# --- run stage: solo el jar y un JRE, sin toolchain de build ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S comercio && adduser -S comercio -G comercio
COPY --from=build /workspace/core/target/core.jar app.jar
USER comercio

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
