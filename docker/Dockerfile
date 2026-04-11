FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY framework-http/target/framework-http-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xms256m","-Xmx768m","-jar","app.jar"]
