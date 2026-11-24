# JME Reaction Observer Service

This example shows how to set up and run an instance of the jEAP Reaction Observer Service locally.

The [jEAP Reaction Observer Library](https://github.com/jeap-admin-ch/jeap-reaction-observer) runs inside
each microservice of a business application and publishes events describing how the service reacted to an
incoming message or REST request (which messages it produced and which REST calls it made in response). This
Reaction Observer Service instance consumes those events, aggregates them into a queryable model, and exposes
them through a REST API so that other jEAP components (for example the Architecture Repository) can render the
observed system behaviour.

It contains the following modules:

* **jme-reaction-observer-service**: An instance of the Reaction Observer Service, built on top of
  [jeap-reaction-observer-service-instance](https://github.com/jeap-admin-ch/jeap-reaction-observer-service).
* **jme-reaction-observer-auth-scs**: An instance of the
  [jEAP OAuth mock server](https://github.com/jeap-admin-ch/jeap-oauth-mock-server), issuing the tokens the
  REST API of the service is called with.
* **jme-reaction-observer-test**: End-to-end integration tests that verify the service against real
  infrastructure.

It also contains a small demo system of five services whose reactions the Reaction Observer Service observes
(see [Demo system](#demo-system)):

* **jme-ros-order-service**: Consumer with the Reaction Observer Library. Accepts orders, consumes its own
  order submitted event and checks the order by calling the other services.
* **jme-ros-partner-service**: Provider with the Reaction Observer Library. Reserves credit and publishes the
  credit reserved event over the transactional outbox.
* **jme-ros-header-only**: Product catalog, without the Reaction Observer library; will only return the name of
   an executed HTTP operation in a header for a reaction observer.
* **jme-ros-no-messaging**: Price service without jEAP Messaging and without the Reaction Observer library; calls
  the product catalog.
* **jme-ros-external**: Credit rating agency standing for a system outside of jEAP; does not return the name of an
  executed HTTP operation in a header for a reaction observer.

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
[jme-messaging-example](https://github.com/jme-admin-ch/jme-messaging-example), you can instead start only the PostgreSQL databases:

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

## Demo system

The demo system shows which parts of a chain of reactions the Reaction Observer sees. An order is accepted
synchronously and checked asynchronously: product catalog, price, from CHF 1000 the credit rating of an
external agency, and a reservation on the credit limit of the partner.

```text
POST /api/orders
  ▼
jme-ros-order-service ── JmeRosOrderSubmittedEvent ──► Kafka ──► jme-ros-order-service (listener on its own event)
  1. GET  /api/products/{productId} ──────────────────► jme-ros-header-only
  2. GET  /api/prices/{productId} ────────────────────► jme-ros-no-messaging
                                                          └─ GET /api/products/{productId} ──► jme-ros-header-only
  3. only from CHF 1000:
     GET  /api/ratings/{partnerId}[?delay=true] ──────► jme-ros-external
  4. POST /api/partners/{partnerId}/credit-reservations[?fail=true]
                                                   ───► jme-ros-partner-service ── JmeRosCreditReservedEvent ──► Kafka
  5. ── JmeRosOrderCheckedEvent ──► Kafka
```

The order service always calls the product catalog, the price service and the credit reservation, and it
catches every error of a call: the outcome of a call only changes the result in the order checked event
(`ACCEPTED`, `REJECTED`, `REVIEW_REQUIRED`), never which calls are made. Only the call of the external
credit rating depends on the amount. (If the price service is down, there is no amount to rate and to
reserve: the order then requires a review without these two calls. This is not one of the demo cases.)
The unit price is CHF 100 and the credit limit CHF 5000 per reservation,
so the quantity of an order (1 to 1000) selects the case:

| Order | Case | Result |
|---|---|---|
| `quantity` 1–9 | no credit rating | `ACCEPTED` |
| `quantity` 10–50 | credit rating | `ACCEPTED` |
| `quantity` 10–50, `simulateSlowRating: true` | the credit rating answers only after the timeout of the order service (call without response) | `REVIEW_REQUIRED` |
| `quantity` 51–1000 | credit limit exceeded, the partner service answers `409` | `REJECTED` |
| `quantity` 1–50, `simulateFailedReservation: true` | the partner service writes the reservation and its event, then fails with a rollback (`500`) | `REVIEW_REQUIRED` |

The services and events are named after the domain where they stand for common business services (order
service, partner service); the other three services model a specific technical situation and are named
after it. The new services have no security; see the jme-security-example for that. The message types of the
events are defined in the [JME message type registry](https://github.com/jme-admin-ch/jme-message-type-registry).

### What the Reaction Observer Service sees

The order service and the partner service observe by reaction model version 2 of the Reaction Observer Library
(`jeap.reaction.observer.model-version: 2`): next to the messages, the incoming REST requests of a service are
observed as triggers, and its outgoing REST calls as actions. The Reaction Observer Service therefore sees:

* `jme-ros-order-service`: reacts to a request to its start endpoint `POST /api/orders` with
  `JmeRosOrderSubmittedEvent`, and to `JmeRosOrderSubmittedEvent` with `JmeRosOrderCheckedEvent` and the REST
  calls to the product catalog, the price service and the partner service. For an order from CHF 1000 the call of
  the external credit rating belongs to the reaction, too: checking an order shows up as two reactions, without and
  with the credit rating.
* `jme-ros-partner-service`: reacts to a request to `POST /api/partners/{partnerId}/credit-reservations` with
  `JmeRosCreditReservedEvent`. This is the HTTP operation the order service calls, so the chain of an order
  continues across the REST call: start endpoint → `JmeRosOrderSubmittedEvent` → order check → credit reservation
  → `JmeRosCreditReservedEvent`.
* `jme-ros-header-only`, `jme-ros-no-messaging` and `jme-ros-external` report no reactions of their own. They
  appear as the providers of the HTTP operations the order service calls. The call of the price service to the
  product catalog is not seen: the price service does not have the library.

A request counts as a reaction if it has been handled successfully: always with a status below 400, with a status
of 400 to 499 only if it caused at least one action (a message or a call), never with a status of 500 or above.
In the demo system, the partner service therefore does not count the order over the credit limit: it answers
`409` without writing anything. Nor does it count the order with `simulateFailedReservation`: it answers `500`,
and the credit reserved event it had written to the transactional outbox is rolled back. The order service still
counts its call of the partner service in both cases, see below. Requests the library does not observe as triggers,
e.g. those to Actuator or Swagger UI, are described in the documentation of the
[jEAP Reaction Observer Library](https://github.com/jeap-admin-ch/jeap-reaction-observer).

An HTTP operation is named by the service providing it: `jme-ros-partner-service POST
/api/partners/{}/credit-reservations`. The partner service, the product catalog and the price service name their
operations to their callers in the response header `JEAP-REACTION-OBSERVATION`, set by the module
`jeap-reaction-observer-header-starter`. The jEAP parent brings this module to every service; a service does not
have to add it. A service with the library names the triggers of its requests by the same name, so that the
caller's action and the provider's trigger are the same HTTP operation. `jme-ros-external` stands for a system
outside of jEAP and therefore switches the header off (`jeap.reaction.observer.header.enabled: false`). A service
that does not name its operations is still shown: the caller names the operation itself, by the host and the
context the call went to and the method, e.g. `//localhost/jme-ros-external GET /{*}`.

A call that fails is not an action: a call without a response, or with a status of 500 or above from a service
that does not name its operation. The reaction of that execution is not reported at all, so that operational
failures do not show up as reactions of their own. In the demo system this is the order with
`simulateSlowRating`. The order with `simulateFailedReservation` is reported: the partner service answers `500`,
but names its operation. The order service counts the calls not named by their provider and the calls that did
not become an action in the metrics `jeap_reaction_observer_unidentified_http_calls_total` and
`jeap_reaction_observer_dropped_http_calls_total`.

The Reaction Observer Service serves its graphs in two versions. The API v2 (`/api/v2/graphs/**`) shows every
kind of interface a reaction has, e.g. the HTTP operations that trigger it and that it calls. The API v1
(`/api/graphs/**`) shows messages only: a reaction triggered by a REST request is shown there without trigger, and
the two reactions checking an order look alike.

### Running the demo

Start the infrastructure (see [Infrastructure](#infrastructure)), build the project and start the Reaction
Observer Service and the OAuth mock server as described above. Then start the five services of the demo
system, each in its own terminal:

```shell
./mvnw --projects jme-ros-header-only spring-boot:run -Dspring-boot.run.profiles=local
./mvnw --projects jme-ros-no-messaging spring-boot:run -Dspring-boot.run.profiles=local
./mvnw --projects jme-ros-external spring-boot:run -Dspring-boot.run.profiles=local
./mvnw --projects jme-ros-partner-service spring-boot:run -Dspring-boot.run.profiles=local
./mvnw --projects jme-ros-order-service spring-boot:run -Dspring-boot.run.profiles=local
```

| Service | Swagger UI |
|---|---|
| jme-ros-order-service | http://localhost:8091/jme-ros-order-service/swagger-ui/index.html |
| jme-ros-partner-service | http://localhost:8092/jme-ros-partner-service/swagger-ui/index.html |
| jme-ros-header-only | http://localhost:8093/jme-ros-header-only/swagger-ui/index.html |
| jme-ros-no-messaging | http://localhost:8094/jme-ros-no-messaging/swagger-ui/index.html |
| jme-ros-external | http://localhost:8095/jme-ros-external/swagger-ui/index.html |

Submit an order, e.g.:

```shell
curl -H 'Content-Type: application/json' \
  -d '{"partnerId": "P1", "productId": "X1", "quantity": 12, "simulateSlowRating": false, "simulateFailedReservation": false}' \
  http://localhost:8091/jme-ros-order-service/api/orders
```

In the `local` profile, the services with the library report their observed reactions every 10 seconds, and
the Reaction Observer Service rebuilds its graph every 10 seconds. Trigger the aggregation of today's
observations and look at the graph of a component (tokens as in [Security](#security)):

```shell
curl -H "Authorization: Bearer $WRITE_TOKEN" \
  http://localhost:8080/jme-reaction-observer-service/api/management/aggregate-data/$(date +%F)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/jme-reaction-observer-service/api/graphs/components/jme-ros-order-service
```

The same graph in the API v2, and the index of the interfaces with the path of the graph of each interface:

```shell
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/jme-reaction-observer-service/api/v2/graphs/components/jme-ros-order-service
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/jme-reaction-observer-service/api/v2/graphs/interfaces
```

The graph of the HTTP operation `jme-ros-partner-service POST /api/partners/{}/credit-reservations` shows where the
chain of an order crosses the REST call: the reactions of the order service calling the operation, and the reaction
of the partner service the operation triggers. Its path is in the index of the HTTP operations
(`/api/v2/graphs/interfaces?interfaceType=http_request`).

The Swagger UI of the Reaction Observer Service lists the API v2 in a group of its own.

`WRITE_TOKEN` is a token of the client `jme-reaction-observer-operator`.

The version of the Reaction Observer Library is managed by the jEAP parent. To lift a single service to
another version, set the property `jeap-reaction-observer.version` in the POM of that service.

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
3. `ReactionObserverExampleIT`: publish reaction-observer events onto the real Kafka broker and verify them
   through the REST API. One of the reactions calls two HTTP operations, one named by the service providing it
   and one named by the caller: the API v1 shows the reaction with its messages only, the API v2 with the HTTP
   operations as well, and the graph of each HTTP operation is fetched with the path from the index of the
   interfaces.
4. `DemoSystemIT`: additionally start the five services of the demo system, submit orders running through all
   cases of the demo system and verify through the REST API what the Reaction Observer Service sees of the
   resulting chains (see [What the Reaction Observer Service sees](#what-the-reaction-observer-service-sees)),
   in the API v1 and in the API v2.
5. Stop all services and containers.

Ensure Docker is running and ports 5433, 5434, 7781 and 9092 are available.

### Running on CI

On CI the `CI` environment variable must be set. This activates the `ci` Spring profile, which uses
`docker-compose-ci.yml` as an overlay (removing host port bindings, using the container hostnames, and
switching to the broker's internal, unauthenticated listener). On CI an isolated Docker network is used to
allow for parallel builds.

## Changes

This project is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part of the open source distribution of JME. See [github.com/jme-admin-ch/jme](https://github.com/jme-admin-ch/jme)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
