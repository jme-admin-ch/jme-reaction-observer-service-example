package ch.admin.bit.jeap.jme.reactionobserver.test;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/**
 * Requests a service has answered, per status, as counted by the Spring Boot metric {@code http.server.requests} and
 * exposed on the Prometheus endpoint of the jEAP monitoring starter. Every test run starts the services anew, so the
 * counts contain only the requests of the current run.
 */
final class HttpServerRequests {

    private static final String METRIC = "http_server_requests_seconds_count";
    private static final Pattern STATUS = Pattern.compile("status=\"(\\d+)\"");
    private static final String PROMETHEUS_USER = "prometheus";
    private static final String PROMETHEUS_PASSWORD = "secret";

    private HttpServerRequests() {
    }

    /**
     * @param baseUrl base URL of the service
     * @param uri     the URI template of the endpoint, e.g. {@code /api/prices/{productId}}
     * @return number of requests per status code
     */
    static Map<Integer, Integer> countByStatus(String baseUrl, String uri) {
        String metrics = given().baseUri(baseUrl).auth().preemptive().basic(PROMETHEUS_USER, PROMETHEUS_PASSWORD)
                .when().get("/actuator/prometheus")
                .then().statusCode(200)
                .extract().asString();
        Map<Integer, Integer> counts = new TreeMap<>();
        String uriLabel = "uri=\"" + uri + "\"";
        metrics.lines()
                .filter(line -> line.startsWith(METRIC + "{") && line.contains(uriLabel))
                .forEach(line -> {
                    Matcher status = STATUS.matcher(line);
                    if (status.find()) {
                        int count = (int) Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
                        counts.merge(Integer.parseInt(status.group(1)), count, Integer::sum);
                    }
                });
        return counts;
    }
}
