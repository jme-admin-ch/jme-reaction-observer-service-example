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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *     <li>What the Reaction Observer Service sees of the resulting chains. The order service and the partner service
 *     observe by reaction model version 2: next to the messages, the Reaction Observer Service sees the REST calls of
 *     the order service as actions. A call is named by the called service if it names its operations in the response
 *     header (partner service, product catalog, price service), and by the order service itself, by host, context and
 *     method, if it does not (external credit rating). Incoming REST requests are not observed as triggers yet.</li>
 * </ol>
 * What the Reaction Observer Service sees is read through both versions of its graph API: the API v2 shows every kind
 * of interface, the API v1 messages only.
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
    private static final String ROS_HOST_URL = "http://localhost:" + SERVICE_PORTS.get(1);
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

    // The REST calls of the order service as the Reaction Observer Service shows them. The first three are named by
    // the called service in its response header, the last one by the order service: the external credit rating does
    // not name its operations. The services of the test run on localhost and differ in the context only.
    private static final String PRODUCT_OPERATION = HEADER_ONLY + " GET /api/products/{}";
    private static final String PRICE_OPERATION = NO_MESSAGING + " GET /api/prices/{}";
    private static final String CREDIT_RESERVATION_OPERATION = PARTNER_SERVICE + " POST /api/partners/{}/credit-reservations";
    private static final String RATING_TARGET = "//localhost/" + EXTERNAL;
    private static final String RATING_OPERATION = RATING_TARGET + " GET /{*}";

    // The reactions of the order service: it publishes the order submitted event without trigger (the start endpoint
    // is not observed), and it checks a submitted order without and with the external credit rating
    private static final String ORDER_SUBMISSION = reaction(null, ORDER_SUBMITTED);
    private static final String ORDER_CHECK = reaction(ORDER_SUBMITTED,
            ORDER_CHECKED, PRODUCT_OPERATION, PRICE_OPERATION, CREDIT_RESERVATION_OPERATION);
    private static final String ORDER_CHECK_WITH_RATING = reaction(ORDER_SUBMITTED,
            ORDER_CHECKED, PRODUCT_OPERATION, PRICE_OPERATION, CREDIT_RESERVATION_OPERATION, RATING_OPERATION);
    // The API v1 shows messages only: the two ways to check an order look alike
    private static final String ORDER_CHECK_MESSAGES_ONLY = reaction(ORDER_SUBMITTED, ORDER_CHECKED);
    // The reaction of the partner service: it publishes the credit reserved event without trigger (REST requests are
    // not observed as triggers yet)
    private static final String CREDIT_RESERVATION = reaction(null, CREDIT_RESERVED);

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
                "jme.ros.external.delay", "8s"));
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
                // generous, because the first calls to the freshly started services are slow on a busy build agent
                "jme.ros.order-service.read-timeout", "5s"));

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
    void demoSystemRunsThroughAllCasesAndReactionObserverServiceSeesTheMessagesAndRestCallsOfTheChains() {
        // given/when: the first order runs through alone and identifies the reactions, then the others follow
        Map<String, DemoCase> demoCasesByOrderId = new LinkedHashMap<>();
        DemoCase firstCase = DEMO_CASES.getFirst();
        demoCasesByOrderId.put(submitOrder(firstCase), firstCase);
        awaitReactionObserverServiceKnowsThePublishingServices();
        DEMO_CASES.subList(1, DEMO_CASES.size())
                .forEach(demoCase -> demoCasesByOrderId.put(submitOrder(demoCase), demoCase));
        // and: the first demo case once more at the end. This shows nothing new, it safeguards the test: the first
        // demo case is the only one checked without the credit rating, and a reaction counted only once could get
        // lost, see awaitReactionObserverServiceKnowsThePublishingServices
        demoCasesByOrderId.put(submitOrder(firstCase), firstCase);

        // then: every order has been checked with the expected result
        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> demoCasesByOrderId.forEach((orderId, demoCase) ->
                assertThat(demoSystemEvents.orderCheckResult(orderId))
                        .as("result of the order %s", demoCase.name())
                        .contains(demoCase.expectedResult())));

        // and: the credit has been reserved (and the reservation committed) for the orders with a reservation...
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> demoCasesByOrderId.forEach((orderId, demoCase) -> {
            if (demoCase.creditReserved()) {
                assertThat(demoSystemEvents.creditReserved(orderId)).as("credit reserved for %s", demoCase.name()).isTrue();
            }
        }));
        // ...and only for them: no credit reserved event arrives for the other orders (credit limit exceeded, rollback)
        // during a hold time. Kafka does not guarantee an order across partitions, so the absence of an event can
        // only be shown for a limited time: an event committed by mistake is published right after its commit, long
        // before the hold time ends.
        await().during(NO_EVENT_HOLD_TIME).atMost(NO_EVENT_HOLD_TIME.plusSeconds(5)).untilAsserted(() ->
                demoCasesByOrderId.forEach((orderId, demoCase) -> assertThat(demoSystemEvents.creditReserved(orderId))
                        .as("credit reserved for %s", demoCase.name())
                        .isEqualTo(demoCase.creditReserved())));

        // and: every endpoint has been called as expected: the product catalog directly and by the price service, the
        // credit rating only from CHF 1000 (including the delayed rating, which the service still answers after the
        // timeout of the order service), and the credit reservation with all its outcomes
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(HttpServerRequests.countByStatus(HEADER_ONLY_BASE_URL, "/api/products/{productId}"))
                    .containsExactly(entry(200, 12));
            assertThat(HttpServerRequests.countByStatus(NO_MESSAGING_BASE_URL, "/api/prices/{productId}"))
                    .containsExactly(entry(200, 6));
            assertThat(HttpServerRequests.countByStatus(EXTERNAL_BASE_URL, "/api/ratings/{partnerId}"))
                    .containsExactly(entry(200, 4));
            assertThat(HttpServerRequests.countByStatus(PARTNER_SERVICE_BASE_URL, "/api/partners/{partnerId}/credit-reservations"))
                    .containsExactly(entry(201, 4), entry(409, 1), entry(500, 1));
        });

        // and: the order service has observed its REST calls as expected. The external credit rating never names its
        // operation: three ratings answered without the header, the delayed one did not answer at all. Only this
        // call without a response has failed: the check of that order is not reported as a reaction. A status of
        // 409 or 500 of the partner service is no failed call, because the partner service names its operation.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(ObservationMetrics.unidentifiedHttpCalls(ORDER_SERVICE_BASE_URL))
                    .containsExactly(entry("no_header", 3), entry("no_response", 1));
            assertThat(ObservationMetrics.droppedHttpCalls(ORDER_SERVICE_BASE_URL))
                    .containsExactly(entry("call_failed", 1));
        });

        // and: once observed, aggregated and built into the graph, the Reaction Observer Service sees the messages
        // and the REST calls of the chains
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3))
                .untilAsserted(this::assertReactionObserverServiceSeesTheMessagesAndRestCallsOfTheChains);

        // and: the check of the order with the credit rating without response has not been counted as a reaction
        assertCheckOfTheOrderWithoutRatingResponseIsNotCounted();

        // and: the index of the interfaces of the API v2 leads to the graph of an event and of an HTTP operation
        assertIndexOfInterfacesLeadsToTheGraphOfTheOrderSubmittedEvent();
        assertIndexOfInterfacesLeadsToTheGraphOfTheCreditReservationOperation();

        // and: the services without the library report no reactions of their own: they are no components
        assertThat(getComponentNames()).doesNotContain(HEADER_ONLY, NO_MESSAGING, EXTERNAL);
    }

    /**
     * Aggregates the observations of today and verifies what the Reaction Observer Service sees:
     * <ul>
     * <li>the order service: publishing the order submitted event without trigger (the start endpoint is not
     * observed), and checking a submitted order - the order checked event and the REST calls to the product
     * catalog, the price service and the partner service, and for orders from CHF 1000 the call of the external
     * credit rating in a reaction of its own</li>
     * <li>the partner service: publishing the credit reserved event without trigger (REST requests are not
     * observed as triggers yet)</li>
     * </ul>
     * The API v2 shows the REST calls as HTTP operations. The API v1 shows the same reactions with their messages
     * only. The reaction model version of a reaction is not verified: the API v2 does not derive it yet.
     */
    private void assertReactionObserverServiceSeesTheMessagesAndRestCallsOfTheChains() {
        triggerAggregation(LocalDate.now().toString());

        JsonPath graphOfOrderService = graphOfApiV2(ORDER_SERVICE);
        JsonPath graphOfPartnerService = graphOfApiV2(PARTNER_SERVICE);
        assertThat(ReactionGraphs.ofApiV2(graphOfOrderService)).containsExactlyInAnyOrder(
                ORDER_SUBMISSION, ORDER_CHECK, ORDER_CHECK_WITH_RATING);
        assertThat(ReactionGraphs.ofApiV2(graphOfPartnerService)).containsExactly(CREDIT_RESERVATION);

        // The called service is the provider of an HTTP operation it has named itself; the operation the order
        // service has named is provided by "the target", i.e. the host and the context of the call
        assertThat(ReactionGraphs.httpOperationsOfApiV2(graphOfOrderService)).containsExactlyInAnyOrder(
                "provider=" + HEADER_ONLY + ", method=GET, path=/api/products/{}",
                "provider=" + NO_MESSAGING + ", method=GET, path=/api/prices/{}",
                "provider=" + PARTNER_SERVICE + ", method=POST, path=/api/partners/{}/credit-reservations",
                "provider=" + RATING_TARGET + ", method=GET, path=/{*}");
        assertThat(ReactionGraphs.httpOperationsOfApiV2(graphOfPartnerService)).isEmpty();

        assertThat(reactionsOf(ORDER_SERVICE)).containsExactlyInAnyOrder(
                ORDER_SUBMISSION, ORDER_CHECK_MESSAGES_ONLY, ORDER_CHECK_MESSAGES_ONLY);
        assertThat(reactionsOf(PARTNER_SERVICE)).containsExactly(CREDIT_RESERVATION);
    }

    /**
     * Verifies that the check of the order with the credit rating without response is not counted as a reaction.
     * The order service still calls the other services and publishes the order checked event for that order: were
     * its check reported without the failed call, it would be the reaction checking an order without the credit
     * rating. That reaction is in the graph anyway, so only its count tells: two orders are checked without the
     * credit rating (the first demo case, submitted twice) and three with it.
     * <p>
     * The counts are upper bounds: the Reaction Observer Service discards the counts of a reaction it does not know
     * yet, see {@link #awaitReactionObserverServiceKnowsThePublishingServices()}, so a count can be too low, but
     * never too high. All counts have been reported by now: every order has been checked more than
     * {@link #NO_EVENT_HOLD_TIME} ago, several reporting periods of the order service.
     */
    private void assertCheckOfTheOrderWithoutRatingResponseIsNotCounted() {
        triggerAggregation(LocalDate.now().toString());

        assertThat(ReactionGraphs.mediansOfApiV2(graphOfApiV2(ORDER_SERVICE)))
                .hasEntrySatisfying(ORDER_CHECK, count -> assertThat(count).isLessThanOrEqualTo(2))
                .hasEntrySatisfying(ORDER_CHECK_WITH_RATING, count -> assertThat(count).isLessThanOrEqualTo(3));
    }

    /**
     * Verifies that the index of the interfaces of the API v2 lists the events, and that the path of the order
     * submitted event leads to its graph: the reaction publishing the event and the reaction to it, both of the
     * order service.
     */
    private void assertIndexOfInterfacesLeadsToTheGraphOfTheOrderSubmittedEvent() {
        Map<String, String> pathsOfEvents = pathsOfInterfaces("event");
        assertThat(pathsOfEvents).containsKeys(ORDER_SUBMITTED, ORDER_CHECKED, CREDIT_RESERVED);
        assertThat(reactionsAt(pathsOfEvents.get(ORDER_SUBMITTED))).containsExactlyInAnyOrder(
                ORDER_SUBMISSION, ORDER_CHECK, ORDER_CHECK_WITH_RATING);
    }

    /**
     * Verifies that the index of the interfaces of the API v2 lists the HTTP operations the order service calls,
     * and that the path of the credit reservation leads to its graph: the two reactions of the order service
     * calling it. The partner service providing the operation does not appear, because incoming REST requests are
     * not observed as triggers yet. The index covers the whole Reaction Observer Service and may list the HTTP
     * operations of other tests, too: that the order service calls no other operations is verified on its graph.
     */
    private void assertIndexOfInterfacesLeadsToTheGraphOfTheCreditReservationOperation() {
        Map<String, String> pathsOfHttpOperations = pathsOfInterfaces("http_request");
        assertThat(pathsOfHttpOperations).containsKeys(
                PRODUCT_OPERATION, PRICE_OPERATION, CREDIT_RESERVATION_OPERATION, RATING_OPERATION);
        assertThat(reactionsAt(pathsOfHttpOperations.get(CREDIT_RESERVATION_OPERATION))).containsExactlyInAnyOrder(
                ORDER_CHECK, ORDER_CHECK_WITH_RATING);
    }

    /**
     * Waits until the Reaction Observer Service has stored a reaction of each service publishing events.
     * <p>
     * The service discards the counts of reactions it doesn't know yet, and a reaction's identification and its counts
     * reach it on different topics. If all orders were submitted at once, a single report could carry all counts of
     * the reaction publishing the order submitted event, overtake its identification and be lost, and the reaction
     * would never appear in the graph.
     * <p>
     * The service can only be asked for the components it knows, not for single reactions, but that is enough for the
     * four reactions of this test:
     * <ul>
     * <li>Publishing the order submitted event is the first reaction the order service identifies, so it is stored as
     * soon as the order service is known.</li>
     * <li>Publishing the credit reserved event is the only reaction of the partner service.</li>
     * <li>Checking an order without the credit rating cannot have all its counts in one report: only the first demo
     * case is checked this way, which is why the test submits it once more at the end. The orders are checked one
     * after the other, with the order with the slow rating in between. That order takes the whole read timeout, which
     * is longer than the reporting period. The last order is counted in a later report, when the reaction has long
     * been stored.</li>
     * <li>Checking an order with the credit rating cannot have all its counts in one report for the same reason: the
     * order with the slow rating is checked between the first order with a rating and the others.</li>
     * </ul>
     */
    private void awaitReactionObserverServiceKnowsThePublishingServices() {
        await().atMost(Duration.ofMinutes(1)).untilAsserted(() ->
                assertThat(getComponentNames()).contains(ORDER_SERVICE, PARTNER_SERVICE));
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
     * The reactions of a component in the graph of the API v1 of the Reaction Observer Service, in the notation
     * "trigger -> {actions}", with an empty trigger for reactions without trigger.
     */
    private List<String> reactionsOf(String component) {
        Response response = given().baseUri(ROS_BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/graphs/components/{component}", component);
        if (response.statusCode() == 404) {
            return List.of();
        }
        return ReactionGraphs.ofApiV1(response.then().statusCode(200).extract().jsonPath());
    }

    /** The graph of a component of the API v2. */
    private JsonPath graphOfApiV2(String component) {
        return given().baseUri(ROS_BASE_URL).basePath("/api").auth().oauth2(readToken)
                .when().get("/v2/graphs/components/{component}", component)
                .then().statusCode(200)
                .extract().jsonPath();
    }

    /** The paths of the graphs of the interfaces of a type by their FQN, from the index of the API v2. */
    private Map<String, String> pathsOfInterfaces(String interfaceType) {
        List<Map<String, String>> entries = given().baseUri(ROS_BASE_URL).basePath("/api").auth().oauth2(readToken)
                .queryParam("interfaceType", interfaceType)
                .when().get("/v2/graphs/interfaces")
                .then().statusCode(200)
                .extract().jsonPath().getList("entries");
        return entries.stream().collect(Collectors.toMap(entry -> entry.get("fqn"), entry -> entry.get("path")));
    }

    /** The reactions of the graph of the API v2 at a path taken from an index; the path has the context path. */
    private List<String> reactionsAt(String path) {
        return ReactionGraphs.ofApiV2(given().baseUri(ROS_HOST_URL).auth().oauth2(readToken)
                .when().get(path)
                .then().statusCode(200)
                .extract().jsonPath());
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

    /**
     * A reaction in the notation of {@link ReactionGraphs}.
     *
     * @param trigger the trigger, null for a reaction without trigger
     */
    private static String reaction(String trigger, String... actions) {
        return ReactionGraphs.notation(trigger, List.of(actions));
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
