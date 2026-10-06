FROM m.daocloud.io/docker.io/library/maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml maven-central-settings.xml ./
COPY src ./src
RUN mvn -DskipTests package

FROM m.daocloud.io/docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/melo-cma-nwp-0.0.1-SNAPSHOT.jar app.jar
ENV SERVER_PORT=8080
ENV CMA_GFS_ROOT=/data/CMA-GFS-DATA
ENV CMA_MESO_ROOT=/data/CMA-MESO-DATA
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
