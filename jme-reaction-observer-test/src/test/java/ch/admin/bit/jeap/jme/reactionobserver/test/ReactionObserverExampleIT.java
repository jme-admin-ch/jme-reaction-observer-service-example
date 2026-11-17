package ch.admin.bit.jeap.jme.reactionobserver.test;

import ch.admin.bit.jeap.jme.test.BootServiceSpringIntegrationTestBase;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionIdentifiedV2EventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.ReactionsObservedEventBuilder;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestObservation;
import ch.admin.bit.jeap.reaction.observer.service.test.model.TestReaction;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Starts the real, packaged jme-reaction-observer-service against the docker-compose infrastructure
 * (real PostgreSQL and Kafka), publishes real ReactionIdentifiedEvent / ReactionsObservedEvent messages on
 * Kafka - exactly as the jEAP Reaction Observer Library would from another service - and verifies the data
 * becomes visible through the service's own REST API, with tokens of the OAuth mock server
 * (jme-reaction-observer-auth-scs).
 */
class ReactionObserverExampleIT extends BootServiceSpringIntegrationTestBase {

    private static final List<Integer> SERVICE_PORTS = reserveFreePorts(2);
    private static final int AUTH_PORT = SERVICE_PORTS.getFirst();
    private static final int SERVICE_PORT = SERVICE_PORTS.get(1);
    private static final String AUTH_BASE_URL = "http://localhost:" + AUTH_PORT + "/jme-reaction-observer-auth-scs";
    private static final String HOST_URL = "http://localhost:" + SERVICE_PORT;
    private static final String BASE_URL = HOST_URL + "/jme-reaction-observer-service";

    // The path of an HTTP operation its caller named: the caller does not know the path patterns of the called service
    private static final String ANY_PATH = "/{*}";

    @Autowired
    private KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;

    private String readToken;
    private String writeToken;

    @BeforeAll
    static void startServices() throws Exception {
        startService("jme-reaction-observer-auth-scs", AUTH_BASE_URL, Map.of(
                "server.port", String.valueOf(AUTH_PORT),
                "mockserver.base-url", AUTH_BASE_URL));
        startService("jme-reaction-observer-service", BASE_URL, Map.of(
                "server.port", String.valueOf(SERVICE_PORT),
                "jeap.security.oauth2.resourceserver.authorization-server.issuer", AUTH_BASE_URL,
                "jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri",
                AUTH_BASE_URL + "/.well-known/jwks.json"));

        // The health check above only confirms the HTTP server and database are up - it says nothing about
        // whether the Kafka consumer group has finished its initial rebalance. Since these consumers use the
        // default auto.offset.reset=latest and start with no committed offset, any message published before
        // the rebalance completes would be silently skipped forever.
        KafkaConsumerGroupAwaiter.waitForAssignment("jme-reaction-observer-service", "jme-reaction-identified");
        KafkaConsumerGroupAwaiter.waitForAssignment("jme-reaction-observer-service", "jme-reactions-observed");
    }

    @BeforeEach
    void fetchTokens() {
        readToken = fetchAccessToken(AUTH_BASE_URL, "jme-reaction-observer-reader", "secret");
        writeToken = fetchAccessToken(AUTH_BASE_URL, "jme-reaction-observer-operator", "secret");
    }

    @Test
    void identifiedReactionIsPersistedAndExposedViaTheReadApi() {
        // given: a reaction identified by a service consuming a message
        String system = "jme-messaging-example-" + UUID.randomUUID();
        String component = "jme-messaging-subscriber-service";
        TestReaction reaction = new TestReaction(
                TestObservation.ofEvent("MyDomainEvent"),
                List.of(TestObservation.ofCommand("MyCommand")),
                "reaction-" + UUID.randomUUID());
        ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedV2EventBuilder.buildEvent(system, component, reaction);

        // when: the reaction-identified event is published on Kafka, as the Reaction Observer Library would
        sendSync("jme-reaction-identified", identifiedEvent);

        // then: the system and component become visible through the read API
        await().untilAsserted(() -> assertThat(getSystemNames()).contains(system));
        await().untilAsserted(() -> assertThat(getComponentNames()).contains(component));
    }

    @Test
    void observedReactionCountIsAggregatedAndExposedViaTheStatisticsApi() {
        // given: a reaction that has already been identified
        String system = "jme-messaging-example-" + UUID.randomUUID();
        String component = "jme-messaging-subscriber-service";
        TestReaction reaction = new TestReaction(
                TestObservation.ofEvent("MyDomainEvent"),
                List.of(TestObservation.ofCommand("MyCommand")),
                "reaction-" + UUID.randomUUID());
        ReactionIdentifiedEvent identifiedEvent = ReactionIdentifiedV2EventBuilder.buildEvent(system, component, reaction);
        sendSync("jme-reaction-identified", identifiedEvent);
        await().untilAsserted(() -> assertThat(getSystemNames()).contains(system));

        // when: an observation count for that reaction is published on Kafka
        Instant now = Instant.now();
        Instant timeframeStart = now.minusSeconds(300);
        ReactionsObservedEvent observedEvent = new ReactionsObservedEventBuilder(component, system)
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(reaction.id(), 3))
                .timeframe(timeframeStart, now)
                .build();
        sendSync("jme-reactions-observed", observedEvent);

        // then: once aggregated, the component's last observation date is exposed through the statistics API
        String observationDay = observationDayOf(timeframeStart);
        await().untilAsserted(() -> {
            triggerAggregation(observationDay);
            assertThat(getLastObservationDatePerComponent()).containsKey(component);
        });
    }

    @Test
    void reactionCallingHttpOperationsIsExposedWithItsMessagesByTheApiV1AndWholeByTheApiV2() {
        // given: a reaction to an event that sends a command and calls two HTTP operations of other services, as the
        // Reaction Observer Library reports it once it observes REST calls:
        // - a service that names its operations to its callers: the operation is the provider, the method and the
        //   path pattern
        // - a service that does not, e.g. a system outside of jEAP: the caller names the operation itself, by where
        //   the call went (the called host and the context of the called service) and the method, for any path
        // The events have the reaction model version 1, the default of the builders: the Reaction Observer Service
        // does not evaluate the model version yet, and no released version of the library reports a newer one.
        String system = "jme-messaging-example-" + UUID.randomUUID();
        String component = "jme-http-consumer-service-" + UUID.randomUUID();
        String provider = "jme-http-provider-service-" + UUID.randomUUID();
        String pattern = "/api/partners/{}/credit-reservations";
        String operationNamedByItsProvider = provider + " POST " + pattern;
        String target = "//rating-agency-" + UUID.randomUUID() + ".example.org/rating-service";
        String operationNamedByTheCaller = target + " GET " + ANY_PATH;
        TestReaction reaction = new TestReaction(
                TestObservation.ofEvent("MyDomainEvent"),
                List.of(TestObservation.ofCommand("MyCommand"),
                        TestObservation.ofHttpRequest(provider, "POST", pattern),
                        TestObservation.ofHttpRequest(target, "GET", ANY_PATH)),
                "reaction-" + UUID.randomUUID());
        sendSync("jme-reaction-identified", ReactionIdentifiedV2EventBuilder.buildEvent(system, component, reaction));
        await().untilAsserted(() -> assertThat(getComponentNames()).contains(component));

        // when: the reaction is observed three times
        Instant now = Instant.now();
        Instant timeframeStart = now.minusSeconds(300);
        sendSync("jme-reactions-observed", new ReactionsObservedEventBuilder(component, system)
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(reaction.id(), 3))
                .timeframe(timeframeStart, now)
                .build());

        // then: once aggregated and built into the graph, both versions of the graph API show the reaction with the
        // same median - the API v1 with its messages only, the API v2 whole, with the HTTP operations it calls (the
        // actions in the notation are sorted by name: "//…" before "MyCommand" before "jme-…")
        String reactionInApiV2 = "MyDomainEvent -> {" + operationNamedByTheCaller + ", MyCommand, "
                + operationNamedByItsProvider + "}";
        String observationDay = observationDayOf(timeframeStart);
        await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
            triggerAggregation(observationDay);
            JsonPath apiV1 = getGraph("/api/graphs/components/{component}", component);
            assertThat(ReactionGraphs.ofApiV1(apiV1)).containsExactly("MyDomainEvent -> {MyCommand}");
            assertThat(apiV1.getList("graph.nodes.nodeType", String.class))
                    .containsExactlyInAnyOrder("MESSAGE", "MESSAGE", "REACTION");
            assertThat(apiV1.getInt("graph.nodes.find { it.nodeType == 'REACTION' }.median")).isEqualTo(3);

            JsonPath apiV2 = getGraph("/api/v2/graphs/components/{component}", component);
            assertThat(ReactionGraphs.ofApiV2(apiV2)).containsExactly(reactionInApiV2);
            assertThat(apiV2.getList("graph.nodes.nodeType", String.class))
                    .containsExactlyInAnyOrder("MESSAGE", "MESSAGE", "HTTP_OPERATION", "HTTP_OPERATION", "REACTION");
            assertThat(apiV2.getInt("graph.nodes.find { it.nodeType == 'REACTION' }.median")).isEqualTo(3);
            assertThat(httpOperation(apiV2, operationNamedByItsProvider))
                    .containsEntry("interfaceType", "http_request")
                    .containsEntry("provider", provider)
                    .containsEntry("method", "POST")
                    .containsEntry("path", pattern);
            // the operation the caller named is an HTTP operation like the other one: its provider starts with "//"
            // and is where the call went instead of the name of a component
            assertThat(httpOperation(apiV2, operationNamedByTheCaller))
                    .containsEntry("interfaceType", "http_request")
                    .containsEntry("provider", target)
                    .containsEntry("method", "GET")
                    .containsEntry("path", ANY_PATH);
        });

        // and: the index of the interfaces lists both HTTP operations, and the path of an entry leads to the graph of
        // its operation, which has the entity tag the index names and the reaction calling the operation, whole
        List<Map<String, String>> index = getInterfaceIndexEntries("http_request");
        for (String httpOperation : List.of(operationNamedByItsProvider, operationNamedByTheCaller)) {
            Map<String, String> indexEntry = index.stream()
                    .filter(entry -> httpOperation.equals(entry.get("fqn")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The index of the interfaces has no entry for " + httpOperation));
            Response graphOfTheOperation = given().baseUri(HOST_URL).auth().oauth2(readToken)
                    .when().get(indexEntry.get("path"));
            graphOfTheOperation.then().statusCode(200).header("ETag", indexEntry.get("etag"));
            assertThat(ReactionGraphs.ofApiV2(graphOfTheOperation.jsonPath())).containsExactly(reactionInApiV2);
        }
    }

    @Test
    void apiV2IsDocumentedInAnOpenApiGroupOfItsOwn() {
        // when: the OpenAPI document of the group of the API v2 is fetched, as the Swagger UI of the service does
        JsonPath openApi = given().baseUri(BASE_URL)
                .when().get("/api-docs/reaction-observer-service-api-v2")
                .then().statusCode(200)
                .extract().jsonPath();

        // then: it documents the graphs of the API v2, e.g. the graph of an interface, and not the API v1
        assertThat(openApi.getString("info.version")).isEqualTo("2.0.0");
        assertThat(openApi.<String, Object>getMap("paths"))
                .containsKey("/api/v2/graphs/interfaces/{interfaceId}")
                .doesNotContainKey("/api/graphs");
    }

    private void sendSync(String topic, AvroMessage message) {
        try {
            kafkaTemplate.send(topic, message).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Could not send message to topic " + topic, e);
        }
    }

    private List<String> getSystemNames() {
        return given().baseUri(BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/systems/names")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", String.class);
    }

    private List<String> getComponentNames() {
        return given().baseUri(BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/components/names")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", String.class);
    }

    private JsonPath getGraph(String path, String name) {
        return given().baseUri(BASE_URL).auth().oauth2(readToken)
                .when().get(path, name)
                .then().statusCode(200)
                .extract().jsonPath();
    }

    /** The node of the HTTP operation with the FQN in a graph of the API v2. */
    private static Map<String, Object> httpOperation(JsonPath graph, String fqn) {
        return graph.<Map<String, Object>>getList("graph.nodes").stream()
                .filter(node -> "HTTP_OPERATION".equals(node.get("nodeType")) && fqn.equals(node.get("fqn")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The graph has no HTTP operation " + fqn));
    }

    private List<Map<String, String>> getInterfaceIndexEntries(String interfaceType) {
        return given().baseUri(BASE_URL).basePath("/api").auth().oauth2(readToken)
                .queryParam("interfaceType", interfaceType)
                .when().get("/v2/graphs/interfaces")
                .then().statusCode(200)
                .extract().jsonPath().getList("entries");
    }

    /**
     * The day under which the Reaction Observer Service files an observation count: the day its timeframe starts, in
     * the time zone of the service, which runs on this machine. This is not always today: a timeframe starting
     * shortly before midnight belongs to the day before.
     */
    private static String observationDayOf(Instant timeframeStart) {
        return LocalDate.ofInstant(timeframeStart, ZoneId.systemDefault()).toString();
    }

    private void triggerAggregation(String date) {
        given().baseUri(BASE_URL).basePath("/api").auth().oauth2(writeToken)
                .when().get("/management/aggregate-data/{date}", date)
                .then().statusCode(200);
    }

    private Map<String, String> getLastObservationDatePerComponent() {
        return given().baseUri(BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/statistics/last-observation-date")
                .then().statusCode(200)
                .extract().jsonPath().getMap("$", String.class, String.class);
    }
}
