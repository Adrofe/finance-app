FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY ../pom.xml ./pom.xml
COPY ../investments /app/investments
RUN mvn -f investments/pom.xml -B -U -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/investments/target/investments-*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["sh","-c","java ${JAVA_OPTS:- -Xms256m -Xmx512m} -jar /app/app.jar"]
