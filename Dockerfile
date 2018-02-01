FROM navikt/java:8
COPY target/*.jar /app/app.jar
ENV JAVA_OPTS="-Dspring.profiles.active=remote"
