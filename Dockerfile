FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests dependency:copy-dependencies -DoutputDirectory=target/lib

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/lib ./lib
COPY experiments ./experiments
COPY data ./data
RUN sed -i 's/^mlflow.trackingUri=.*/mlflow.trackingUri=/' experiments/experiment-config.properties
ENTRYPOINT ["java", "-cp", "classes:lib/*", "com.example.mlops.ParameterExperiment"]