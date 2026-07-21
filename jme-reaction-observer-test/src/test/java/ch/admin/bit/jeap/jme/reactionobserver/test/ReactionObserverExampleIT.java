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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.LocalDate;
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
 * becomes visible through the service's own, HTTP-Basic-secured REST API.
 */
class ReactionObserverExampleIT extends BootServiceSpringIntegrationTestBase {

    private static final String BASE_URL = "http://localhost:8080/jme-reaction-observer-service";
    private static final String READ_USER = "read";
    private static final String READ_PASSWORD = "secret";
    private static final String WRITE_USER = "write";
    private static final String WRITE_PASSWORD = "secret";

    @Autowired
    private KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;

    @BeforeAll
    static void startServices() throws Exception {
        startService("jme-reaction-observer-service", BASE_URL);

        // The health check above only confirms the HTTP server and database are up - it says nothing about
        // whether the Kafka consumer group has finished its initial rebalance. Since these consumers use the
        // default auto.offset.reset=latest and start with no committed offset, any message published before
        // the rebalance completes would be silently skipped forever.
        KafkaConsumerGroupAwaiter.waitForAssignment("jme-reaction-observer-service", "jme-reaction-identified");
        KafkaConsumerGroupAwaiter.waitForAssignment("jme-reaction-observer-service", "jme-reactions-observed");
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
        ReactionsObservedEvent observedEvent = new ReactionsObservedEventBuilder(component, system)
                .serviceInstanceIdentifier(UUID.randomUUID())
                .countByReactionId(Map.of(reaction.id(), 3))
                .timeframe(now.minusSeconds(300), now)
                .build();
        sendSync("jme-reactions-observed", observedEvent);

        // then: once aggregated, the component's last observation date is exposed through the statistics API
        String today = LocalDate.now().toString();
        await().untilAsserted(() -> {
            triggerAggregation(today);
            assertThat(getLastObservationDatePerComponent()).containsKey(component);
        });
    }

    private void sendSync(String topic, AvroMessage message) {
        try {
            kafkaTemplate.send(topic, message).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Could not send message to topic " + topic, e);
        }
    }

    private List<String> getSystemNames() {
        return given().baseUri(BASE_URL).basePath("/api").auth().preemptive().basic(READ_USER, READ_PASSWORD)
                .when().get("/systems/names")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", String.class);
    }

    private List<String> getComponentNames() {
        return given().baseUri(BASE_URL).basePath("/api").auth().preemptive().basic(READ_USER, READ_PASSWORD)
                .when().get("/components/names")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", String.class);
    }

    private void triggerAggregation(String date) {
        given().baseUri(BASE_URL).basePath("/api").auth().preemptive().basic(WRITE_USER, WRITE_PASSWORD)
                .when().get("/management/aggregate-data/{date}", date)
                .then().statusCode(200);
    }

    private Map<String, String> getLastObservationDatePerComponent() {
        return given().baseUri(BASE_URL).basePath("/api").auth().preemptive().basic(READ_USER, READ_PASSWORD)
                .when().get("/statistics/last-observation-date")
                .then().statusCode(200)
                .extract().jsonPath().getMap("$", String.class, String.class);
    }
}
