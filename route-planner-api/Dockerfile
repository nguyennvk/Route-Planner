# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy only the build files first so dependency downloads are cached
# and re-used as long as pom.xml doesn't change.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package -DskipTests

# ---------- Stage 2: run the jar ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as a non-root user
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
