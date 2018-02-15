#!/bin/bash

eval "$(cat .env)"

java -jar target/bankkontonummer-kanal-2.0-SNAPSHOT.jar
