FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Met a jour les paquets systeme de l'image de base (openssl, expat...). Sans
# cette ligne on depend de la date a laquelle l'editeur de l'image republie la
# sienne : des correctifs publies restent absents pendant des semaines. En
# echange, la construction est un peu moins reproductible d'un jour a l'autre.
RUN apk upgrade --no-cache
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
