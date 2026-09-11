FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Client Docker : ce service doit pouvoir redémarrer le conteneur de la cible
# réelle (srv-002). Seul le CLIENT est installé — le démon reste celui de
# l'hôte, joint via le socket monté dans docker-compose.yml.
RUN apk add --no-cache docker-cli
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
