# JME Reaction Observer Service

This example shows how to set up and run an instance of the jEAP Reaction Observer Service locally.

The [jEAP Reaction Observer Library](https://github.com/jeap-admin-ch/jeap-reaction-observer) runs inside
each microservice of a business application and publishes events describing how the service reacted to an
incoming message (which messages it produced in response to a consumed message). This Reaction Observer
Service instance consumes those events, aggregates them into a queryable model, and exposes them through a
REST API so that other jEAP components (for example the Architecture Repository) can render the observed
system behaviour.

It contains the following modules:

* **jme-reaction-observer-service**: An instance of the Reaction Observer Service, built on top of
  [jeap-reaction-observer-service-instance](https://github.com/jeap-admin-ch/jeap-reaction-observer-service).
* **jme-reaction-observer-auth-scs**: An instance of the
  [jEAP OAuth mock server](https://github.com/jeap-admin-ch/jeap-oauth-mock-server), issuing the tokens the
  REST API of the service is called with.
* **jme-reaction-observer-test**: End-to-end integration tests that verify the service against real
  infrastructure.

## Changes

This project is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Prerequisites

To use this project, ensure you have the following installed:

1. **Java Development Kit (JDK)**: Version 25.
2. **Docker**: For running the required infrastructure (PostgreSQL and Kafka).

**Note:** Use the provided Maven wrapper to build and run the project.

## Getting started

### Infrastructure

Before the application can be started, the infrastructure (PostgreSQL and Kafka) must be running:

```shell
docker-compose -f docker/docker-compose.yml up
```

If you already have a Kafka cluster running locally, for example the one started by the
[jme-messaging-example](https://github.com/jme-admin-ch/jme-messaging-example), you can instead start only
the PostgreSQL database:

```shell
docker-compose -f docker/docker-compose-db-only.yml up
```

### Build

The project can be built with:

```shell
./mvnw install
```

### Start

The application can then be started using:

```shell
./mvnw --projects jme-reaction-observer-service spring-boot:run -Dspring-boot.run.profiles=local
```

### Access

Once started, the Swagger UI is available at:

[http://localhost:8080/jme-reaction-observer-service/swagger-ui/index.html](http://localhost:8080/jme-reaction-observer-service/swagger-ui/index.html)

The service consumes the `jme-reaction-identified` and `jme-reactions-observed` topics, which are published
by services in the JME example system that embed the jEAP Reaction Observer Library, for example the
[JME messaging example](https://github.com/jme-admin-ch/jme-messaging-example).

### Security

The REST API accepts OAuth2 bearer tokens only, and the Reaction Observer Service refuses to start without
an authorization server and a system name. This example points
`jeap.security.oauth2.resourceserver.authorization-server` at the OAuth mock server of the module
`jme-reaction-observer-auth-scs` on `localhost:8180`. A token is authorized by the semantic role
`jme_@reactions_#read` (read endpoints) or `jme_@reactions_#write` (manual aggregation).

The mock server only has to run when you call the API: its JWKS is only fetched once a request actually
carries a bearer token. Start it and fetch a token of one of the clients configured in
[`application-local.yml`](jme-reaction-observer-auth-scs/src/main/resources/application-local.yml):

```shell
./mvnw --projects jme-reaction-observer-auth-scs spring-boot:run -Dspring-boot.run.profiles=local

TOKEN=$(curl -s -X POST http://localhost:8180/jme-reaction-observer-auth-scs/oauth2/token \
  -d grant_type=client_credentials -d client_id=jme-reaction-observer-reader -d client_secret=secret \
  | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/jme-reaction-observer-service/api/systems/names
```

## Profiles

* **application-local**: Contains all configurations for running the application locally (local PostgreSQL and Kafka).

## Integration Tests

The `jme-reaction-observer-test` module contains end-to-end integration tests that verify the service
against real infrastructure: it starts the docker-compose infrastructure (PostgreSQL and Kafka), starts the
packaged `jme-reaction-observer-service` as a real process, publishes real `ReactionIdentifiedEvent` /
`ReactionsObservedEvent` messages on Kafka - exactly as the jEAP Reaction Observer Library would from
another service - and verifies the data through the service's own REST API, with tokens of the OAuth mock
server in `jme-reaction-observer-auth-scs`.

### Running locally

```shell
# Build and install all local modules
./mvnw install -pl '!:jme-reaction-observer-test'
# Run integration tests
./mvnw verify -pl jme-reaction-observer-test
```

This will:

1. Start the Docker Compose infrastructure (PostgreSQL and Kafka are stopped after the test).
2. Start `jme-reaction-observer-auth-scs` and `jme-reaction-observer-service` as Maven subprocesses, each on a
   free port.
3. Publish reaction-observer events onto the real Kafka broker and verify them through the REST API.
4. Stop all services and containers.

Ensure Docker is running and ports 5433, 7781 and 9092 are available.

### Running on CI

On CI the `CI` environment variable must be set. This activates the `ci` Spring profile, which uses
`docker-compose-ci.yml` as an overlay (removing host port bindings, using the container hostnames, and
switching to the broker's internal, unauthenticated listener). On CI an isolated Docker network is used to
allow for parallel builds.

## Note

This repository is part of the open source distribution of JME. See [github.com/jme-admin-ch/jme](https://github.com/jme-admin-ch/jme)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
