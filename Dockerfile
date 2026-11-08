# Immagine dell'applicazione: solo il runtime Java e il jar prodotto da "mvn clean package".
# La build resta Maven (riproducibile e verificata dai test); il contenitore non scarica dipendenze.
FROM eclipse-temurin:21-jre

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY target/notifiche-csirt-0.1.0-SNAPSHOT.jar app.jar
USER app

ENV TZ=Europe/Rome
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
