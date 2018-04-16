FROM navikt/java:8

# Service code 2896 and service edition code 87
ENV RETRY_MAX_RETRIES=5
ENV RETRY_INTERVAL=5000
ENV BANKACCOUNTNUMBER_CHANGED_TOPIC='aapen-altinn-bankkontonummer-Mottatt'
ENV SERVER_PORT=8080

COPY target/bankkontonummer-kanal*.jar /app/app.jar
