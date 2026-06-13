# --- Build stage: compile the Spring Boot JAR inside Docker ---
FROM maven:3.9-amazoncorretto-17 AS build
WORKDIR /build

# Cache deps in their own layer — only re-downloads when pom.xml changes
COPY BES/pom.xml ./
RUN mvn -B dependency:go-offline

COPY BES/src ./src
RUN mvn -B -DskipTests clean package

# --- Runtime stage: JRE only ---
FROM amazoncorretto:17
COPY --from=build /build/target/*.jar /application.jar
ENTRYPOINT ["java", "-Xmx2048M", "-jar", "/application.jar"]
