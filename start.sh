#!/bin/bash

eval "$(cat .env)"

java -Djavax.net.ssl.trustStore=$TRUSTSTORE_PATH -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD -jar target/bankkontonummer-kanal-2.0-SNAPSHOT.jar
