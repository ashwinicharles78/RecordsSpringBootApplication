FROM openjdk:17

WORKDIR /RecordsSpringBootApplication

COPY target/Records-0.0.1-SNAPSHOT.jar /RecordsSpringBootApplication/app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]