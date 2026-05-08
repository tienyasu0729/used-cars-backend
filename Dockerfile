FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
COPY src ./src

RUN mvn -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker

COPY --from=build /build/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker} -jar /app/app.jar"]
