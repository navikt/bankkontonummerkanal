FROM navikt/java:8

# Service code 2896 and service edition code 87
ENV BANKACCOUNTNUMBER_CHANGED_TOPIC='aapen-altinn-bankkontonummer-Mottatt-v87'
ENV SERVER_PORT=8080

COPY target/bankkontonummer-kanal*.jar /app/app.jar
