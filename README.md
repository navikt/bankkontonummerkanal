# Bankkontonummerkanal
Repository bank account number channel. This is a application written in Java, it is used to update the old AAreg which 
handles the bank account numbers used for paying sick leave in Norway. This application picks up the message from a
Kafka topic sent from [altinnkanal-2](https://github.com/navikt/altinnkanal-2).

### Technologies and tools
* Java
* Kafka
* CXF
* Jetty
* Maven
* JUnit

# Getting started
### Build and run test
`mvn clean build`

### Running locally
To run the application locally you need to mock AAreg WS calls and set their environment variables + point the
application to a kafka cluster. To produce messages its either possible to put messages directly on the kafka topic
or run Altinnkanal-2 and do WS calls against it.

### Contact us
#### Code/project related questions can be sent to
* Kevin Sillerud `kevin.sillerud@nav.no`
* Joakim Kartveit `joakim.kartveit@nav.no`

#### For NAV employees
We are also available at the slack channel #integrasjon for internal communication.
