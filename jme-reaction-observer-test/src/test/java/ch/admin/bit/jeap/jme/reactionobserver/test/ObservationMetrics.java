package ch.admin.bit.jeap.jme.reactionobserver.test;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/**
 * Reads the two metrics of the jEAP Reaction Observer Library on the observation of outgoing HTTP requests, exposed
 * on the Prometheus endpoint of the jEAP monitoring starter. Every test run starts the services anew, so the counts
 * contain only the requests of the current run.
 */
final class ObservationMetrics {

    private static final String UNIDENTIFIED_HTTP_CALLS = "jeap_reaction_observer_unidentified_http_calls_total";
    private static final String DROPPED_HTTP_CALLS = "jeap_reaction_observer_dropped_http_calls_total";
    private static final Pattern REASON = Pattern.compile("reason=\"(\\w+)\"");
    private static final String PROMETHEUS_USER = "prometheus";
    private static final String PROMETHEUS_PASSWORD = "secret";

    private ObservationMetrics() {
    }

    /**
     * @param baseUrl base URL of the service
     * @return number of outgoing HTTP requests whose operation the called service did not name, per reason; a reason
     * that did not occur is left out
     */
    static Map<String, Integer> unidentifiedHttpCalls(String baseUrl) {
        return countByReason(baseUrl, UNIDENTIFIED_HTTP_CALLS);
    }

    /**
     * @param baseUrl base URL of the service
     * @return number of observed HTTP requests that did not become an action, per reason; a reason that did not
     * occur is left out
     */
    static Map<String, Integer> droppedHttpCalls(String baseUrl) {
        return countByReason(baseUrl, DROPPED_HTTP_CALLS);
    }

    private static Map<String, Integer> countByReason(String baseUrl, String metric) {
        String metrics = given().baseUri(baseUrl).auth().preemptive().basic(PROMETHEUS_USER, PROMETHEUS_PASSWORD)
                .when().get("/actuator/prometheus")
                .then().statusCode(200)
                .extract().asString();
        Map<String, Integer> counts = new TreeMap<>();
        metrics.lines()
                .filter(line -> line.startsWith(metric + "{"))
                .forEach(line -> {
                    Matcher reason = REASON.matcher(line);
                    int count = (int) Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
                    if (reason.find() && count > 0) {
                        counts.merge(reason.group(1), count, Integer::sum);
                    }
                });
        return counts;
    }
}
