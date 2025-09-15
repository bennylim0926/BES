FROM amazoncorretto:17

# Build JAR on host first: mvn clean package -DskipTests -f backend/pom.xml
ARG JAR_FILE=BES/target/*.jar

COPY ${JAR_FILE} application.jar

CMD apt-get update -y

ENTRYPOINT [ "java", "-Xmx2048M", "-jar", "/application.jar" ]