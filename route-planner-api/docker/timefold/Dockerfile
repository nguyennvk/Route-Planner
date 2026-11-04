# Timefold's vehicle-routing quickstart (Quarkus), built from source.
# Follows the quickstart README's "packaged application" steps (mvn package, then java -jar),
# but runs Maven inside Docker so it doesn't need to be installed on the host.
# https://github.com/TimefoldAI/timefold-quickstarts/tree/stable/use-cases/vehicle-routing

FROM maven:3.9-eclipse-temurin-21 AS build
# Docker fetches the repo itself; "stable" is the branch the README is on
ADD --keep-git-dir=false https://github.com/TimefoldAI/timefold-quickstarts.git#stable /src
WORKDIR /src/use-cases/vehicle-routing
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Quarkus' runnable layout: quarkus-run.jar plus its lib/, app/ and quarkus/ folders
COPY --from=build /src/use-cases/vehicle-routing/target/quarkus-app/ ./
EXPOSE 8080
CMD ["java", "-jar", "quarkus-run.jar"]
