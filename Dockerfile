FROM amazoncorretto:17-al2023-jdk

COPY target/Records-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
