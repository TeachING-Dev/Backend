FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean build -x test

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
RUN addgroup --system app && adduser --system --ingroup app app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]