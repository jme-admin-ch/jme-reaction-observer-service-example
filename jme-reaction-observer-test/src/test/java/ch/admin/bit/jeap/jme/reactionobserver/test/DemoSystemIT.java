package ch.admin.bit.jeap.jme.reactionobserver.test;

import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.test.BootServiceSpringIntegrationTestBase;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult.ACCEPTED;
import static ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult.REJECTED;
import static ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult.REVIEW_REQUIRED;
import static io.restassured.RestAssured.given;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of the demo system: starts the OAuth mock server, the Reaction Observer Service and the five services
 * of the demo system on free ports, submits orders that run through all demo cases and verifies what the Reaction
 * Observer Service sees of the resulting reaction chains.
 * <p>
 * Call chain of an order (see README):
 * <pre>
 * POST /api/orders -> jme-ros-order-service -- JmeRosOrderSubmittedEvent --> jme-ros-order-service (listener)
 *   1. GET  /api/products/{productId}                     -> jme-ros-header-only
 *   2. GET  /api/prices/{productId}                       -> jme-ros-no-messaging -> GET /api/products/{productId} -> jme-ros-header-only
 *   3. GET  /api/ratings/{partnerId} (from CHF 1000 only) -> jme-ros-external
 *   4. POST /api/partners/{partnerId}/credit-reservations -> jme-ros-partner-service -- JmeRosCreditReservedEvent -->
 *   5. -- JmeRosOrderCheckedEvent -->
 * </pre>
 * The test verifies two things separately:
 * <ol>
 *     <li>What the demo system does, independently of what is observed: the result of every order (order checked
 *     event), the requests every called endpoint has answered (per status), and which orders have reserved credit
 *     (credit reserved event).</li>
 *     <li>What the Reaction Observer Service sees of the resulting chains. Today, this is the picture before the REST
 *     calls are observed: only the message parts of the chains. Once REST calls are also observed the Reaction Observer
 *     Service will also see the HTTP parts.</li>
 * </ol>
 */
@Import(DemoSystemEvents.class)
class DemoSystemIT extends BootServiceSpringIntegrationTestBase {

    private static final List<Integer> SERVICE_PORTS = reserveFreePorts(7);

    private static final String AUTH_SCS = "jme-reaction-observer-auth-scs";
    private static final String ROS = "jme-reaction-observer-service";
    private static final String ORDER_SERVICE = "jme-ros-order-service";
    private static final String PARTNER_SERVICE = "jme-ros-partner-service";
    private static final String HEADER_ONLY = "jme-ros-header-only";
    private static final String NO_MESSAGING = "jme-ros-no-messaging";
    private static final String EXTERNAL = "jme-ros-external";

    private static final String AUTH_BASE_URL = baseUrl(0, AUTH_SCS);
    private static final String ROS_BASE_URL = baseUrl(1, ROS);
    private static final String ORDER_SERVICE_BASE_URL = baseUrl(2, ORDER_SERVICE);
    private static final String PARTNER_SERVICE_BASE_URL = baseUrl(3, PARTNER_SERVICE);
    private static final String HEADER_ONLY_BASE_URL = baseUrl(4, HEADER_ONLY);
    private static final String NO_MESSAGING_BASE_URL = baseUrl(5, NO_MESSAGING);
    private static final String EXTERNAL_BASE_URL = baseUrl(6, EXTERNAL);

    private static final String SERVER_PORT = "server.port";
    // Report the observed reactions every few seconds, so that the test does not wait for the default 5 minutes
    private static final String OBSERVED_EVENT_RATE = "jeap.reaction.observer.events.observed-event-rate-seconds";
    private static final String OBSERVED_EVENT_RATE_SECONDS = "3";

    private static final String ORDER_SUBMITTED = "JmeRosOrderSubmittedEvent";
    private static final String ORDER_CHECKED = "JmeRosOrderCheckedEvent";
    private static final String CREDIT_RESERVED = "JmeRosCreditReservedEvent";

    // How long an event that must not arrive is waited for
    private static final Duration NO_EVENT_HOLD_TIME = Duration.ofSeconds(10);

    /**
     * Orders running through all demo cases (unit price CHF 100, credit rating from CHF 1000, credit limit CHF 5000).
     */
    private static final List<DemoCase> DEMO_CASES = List.of(
            new DemoCase("below the rating threshold", 2, false, false, ACCEPTED, true),
            new DemoCase("with credit rating", 12, false, false, ACCEPTED, true),
            new DemoCase("credit rating without response (timeout)", 12, true, false, REVIEW_REQUIRED, true),
            new DemoCase("credit limit exceeded (409)", 60, false, false, REJECTED, false),
            new DemoCase("credit reservation fails with a rollback (500)", 12, false, true, REVIEW_REQUIRED, false));

    @Autowired
    private DemoSystemEvents demoSystemEvents;

    private String readToken;
    private String writeToken;

    @BeforeAll
    static void startServices() throws Exception {
        startService(AUTH_SCS, AUTH_BASE_URL, Map.of(
                SERVER_PORT, port(0),
                "mockserver.base-url", AUTH_BASE_URL));
        startService(ROS, ROS_BASE_URL, Map.of(
                SERVER_PORT, port(1),
                "jeap.security.oauth2.resourceserver.authorization-server.issuer", AUTH_BASE_URL,
                "jeap.security.oauth2.resourceserver.authorization-server.jwk-set-uri", AUTH_BASE_URL + "/.well-known/jwks.json"));
        startService(HEADER_ONLY, HEADER_ONLY_BASE_URL, Map.of(
                SERVER_PORT, port(4)));
        startService(NO_MESSAGING, NO_MESSAGING_BASE_URL, Map.of(
                SERVER_PORT, port(5),
                "jme.ros.no-messaging.product-catalog-url", HEADER_ONLY_BASE_URL));
        startService(EXTERNAL, EXTERNAL_BASE_URL, Map.of(
                SERVER_PORT, port(6),
                // longer than the read timeout of the order service
                "jme.ros.external.delay", "3s"));
        startService(PARTNER_SERVICE, PARTNER_SERVICE_BASE_URL, Map.of(
                SERVER_PORT, port(3),
                OBSERVED_EVENT_RATE, OBSERVED_EVENT_RATE_SECONDS));
        startService(ORDER_SERVICE, ORDER_SERVICE_BASE_URL, Map.of(
                SERVER_PORT, port(2),
                OBSERVED_EVENT_RATE, OBSERVED_EVENT_RATE_SECONDS,
                "jme.ros.order-service.product-catalog-url", HEADER_ONLY_BASE_URL,
                "jme.ros.order-service.price-service-url", NO_MESSAGING_BASE_URL,
                "jme.ros.order-service.rating-service-url", EXTERNAL_BASE_URL,
                "jme.ros.order-service.partner-service-url", PARTNER_SERVICE_BASE_URL,
                "jme.ros.order-service.read-timeout", "1s"));

        // Messages published before a consumer group has its partitions would be skipped (auto.offset.reset=latest).
        // The listeners of the test itself (DemoSystemEvents) need no wait: they read their topics from the beginning
        // (auto.offset.reset=earliest), and every test run starts with a new broker.
        KafkaConsumerGroupAwaiter.waitForAssignment(ROS, "jme-reaction-identified");
        KafkaConsumerGroupAwaiter.waitForAssignment(ROS, "jme-reactions-observed");
        KafkaConsumerGroupAwaiter.waitForAssignment(ORDER_SERVICE, "jme-ros-order-submitted");
    }

    @BeforeEach
    void fetchTokens() {
        readToken = fetchAccessToken(AUTH_BASE_URL, "jme-reaction-observer-reader", "secret");
        writeToken = fetchAccessToken(AUTH_BASE_URL, "jme-reaction-observer-operator", "secret");
    }

    @Test
    void demoSystemRunsThroughAllCasesAndReactionObserverServiceSeesOnlyTheMessagePartsOfTheChains() {
        // given/when
        Map<DemoCase, String> orderIds = new LinkedHashMap<>();
        DEMO_CASES.forEach(demoCase -> orderIds.put(demoCase, submitOrder(demoCase)));

        // then: every order has been checked with the expected result
        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> orderIds.forEach((demoCase, orderId) ->
                assertThat(demoSystemEvents.orderCheckResult(orderId))
                        .as("result of the order %s", demoCase.name())
                        .contains(demoCase.expectedResult())));

        // and: the credit has been reserved (and the reservation committed) for the orders with a reservation...
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> orderIds.forEach((demoCase, orderId) -> {
            if (demoCase.creditReserved()) {
                assertThat(demoSystemEvents.creditReserved(orderId)).as("credit reserved for %s", demoCase.name()).isTrue();
            }
        }));
        // ...and only for them: no credit reserved event arrives for the other orders (credit limit exceeded, rollback)
        // during a hold time. Kafka does not guarantee an order across partitions, so the absence of an event can
        // only be shown for a limited time: an event committed by mistake is published right after its commit, long
        // before the hold time ends.
        await().during(NO_EVENT_HOLD_TIME).atMost(NO_EVENT_HOLD_TIME.plusSeconds(5)).untilAsserted(() ->
                orderIds.forEach((demoCase, orderId) -> assertThat(demoSystemEvents.creditReserved(orderId))
                        .as("credit reserved for %s", demoCase.name())
                        .isEqualTo(demoCase.creditReserved())));

        // and: every endpoint has been called as expected: the product catalog directly and by the price service, the
        // credit rating only from CHF 1000 (including the delayed rating, which the service still answers after the
        // timeout of the order service), and the credit reservation with all its outcomes
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(HttpServerRequests.countByStatus(HEADER_ONLY_BASE_URL, "/api/products/{productId}"))
                    .containsExactly(entry(200, 10));
            assertThat(HttpServerRequests.countByStatus(NO_MESSAGING_BASE_URL, "/api/prices/{productId}"))
                    .containsExactly(entry(200, 5));
            assertThat(HttpServerRequests.countByStatus(EXTERNAL_BASE_URL, "/api/ratings/{partnerId}"))
                    .containsExactly(entry(200, 4));
            assertThat(HttpServerRequests.countByStatus(PARTNER_SERVICE_BASE_URL, "/api/partners/{partnerId}/credit-reservations"))
                    .containsExactly(entry(201, 3), entry(409, 1), entry(500, 1));
        });

        // and: once observed, aggregated and built into the graph, the Reaction Observer Service sees
        // - the order service: publishing the order submitted event without trigger (the start endpoint is not
        //   observed), and reacting to it with the order checked event; the REST calls are missing
        // - the partner service: publishing the credit reserved event without trigger (REST requests are not observed)
        String today = LocalDate.now().toString();
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
            triggerAggregation(today);
            assertThat(reactionsOf(ORDER_SERVICE)).containsExactlyInAnyOrder(
                    "-> {" + ORDER_SUBMITTED + "}",
                    ORDER_SUBMITTED + " -> {" + ORDER_CHECKED + "}");
            assertThat(reactionsOf(PARTNER_SERVICE)).containsExactly(
                    "-> {" + CREDIT_RESERVED + "}");
        });

        // and: the services without the library do not appear at all
        assertThat(getComponentNames()).doesNotContain(HEADER_ONLY, NO_MESSAGING, EXTERNAL);
    }

    private String submitOrder(DemoCase demoCase) {
        return given().baseUri(ORDER_SERVICE_BASE_URL)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "partnerId", "P1",
                        "productId", "X1",
                        "quantity", demoCase.quantity(),
                        "simulateSlowRating", demoCase.simulateSlowRating(),
                        "simulateFailedReservation", demoCase.simulateFailedReservation()))
                .when().post("/api/orders")
                .then().statusCode(202)
                .extract().path("orderId");
    }

    /**
     * The reactions of a component in the graph of the Reaction Observer Service, in the notation
     * "trigger -> {actions}", with an empty trigger for reactions without trigger.
     */
    private Set<String> reactionsOf(String component) {
        Response response = given().baseUri(ROS_BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/graphs/components/{component}", component);
        if (response.statusCode() == 404) {
            return Set.of();
        }
        JsonPath graph = response.then().statusCode(200).extract().jsonPath();

        Map<Long, String> messageTypes = new HashMap<>();
        Set<Long> reactions = new TreeSet<>();
        for (Map<String, Object> node : graph.<Map<String, Object>>getList("graph.nodes")) {
            long id = ((Number) node.get("id")).longValue();
            if ("MESSAGE".equals(node.get("nodeType"))) {
                messageTypes.put(id, (String) node.get("messageType"));
            } else if (component.equals(node.get("component"))) {
                reactions.add(id);
            }
        }

        Map<Long, String> triggers = new HashMap<>();
        Map<Long, Set<String>> actions = new HashMap<>();
        for (Map<String, Object> edge : graph.<Map<String, Object>>getList("graph.edges")) {
            if ("TRIGGER".equals(edge.get("edgeType"))) {
                long reaction = ((Number) edge.get("targetReactionId")).longValue();
                triggers.put(reaction, messageTypes.get(((Number) edge.get("sourceId")).longValue()));
            } else {
                long reaction = ((Number) edge.get("sourceReactionId")).longValue();
                actions.computeIfAbsent(reaction, _ -> new TreeSet<>())
                        .add(messageTypes.get(((Number) edge.get("targetId")).longValue()));
            }
        }

        return reactions.stream()
                .map(reaction -> (triggers.containsKey(reaction) ? triggers.get(reaction) + " " : "")
                        + "-> {" + String.join(", ", actions.getOrDefault(reaction, Set.of())) + "}")
                .collect(Collectors.toSet());
    }

    private List<String> getComponentNames() {
        return given().baseUri(ROS_BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/components/names")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", String.class);
    }

    private void triggerAggregation(String date) {
        given().baseUri(ROS_BASE_URL).basePath("/api").auth().oauth2(writeToken)
                .when().get("/management/aggregate-data/{date}", date)
                .then().statusCode(200);
    }

    private static String port(int index) {
        return String.valueOf(SERVICE_PORTS.get(index));
    }

    private static String baseUrl(int index, String service) {
        return "http://localhost:" + SERVICE_PORTS.get(index) + "/" + service;
    }

    /**
     * A demo case: an order submitted to the order service and what the demo system is expected to do with it.
     *
     * @param name                      description of the case, used in the assertion messages
     * @param quantity                  quantity of the order; at the unit price of CHF 100 it determines the amount,
     *                                  and thus whether the credit is rated and whether the credit limit is exceeded
     * @param simulateSlowRating        demo switch of the order: the external credit rating answers only after the
     *                                  read timeout of the order service
     * @param simulateFailedReservation demo switch of the order: the partner service fails after it has written the
     *                                  reservation and its event, which rolls both back (500)
     * @param expectedResult            result of the order check expected in the order checked event
     * @param creditReserved            whether the partner service reserves (and commits) credit for the order
     */
    private record DemoCase(String name, int quantity, boolean simulateSlowRating, boolean simulateFailedReservation,
                            JmeRosOrderCheckResult expectedResult, boolean creditReserved) {
    }
}
