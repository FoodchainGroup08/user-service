FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY user-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
