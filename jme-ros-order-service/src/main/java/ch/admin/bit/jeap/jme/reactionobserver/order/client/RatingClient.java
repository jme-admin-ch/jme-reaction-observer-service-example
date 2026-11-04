package ch.admin.bit.jeap.jme.reactionobserver.order.client;

import ch.admin.bit.jeap.jme.reactionobserver.order.OrderServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the external credit rating agency (jme-ros-external), a system outside of jEAP.
 */
@Component
public class RatingClient {

    private final RestClient restClient;

    RatingClient(RestClient.Builder builder, OrderServiceProperties properties) {
        this.restClient = RestClients.create(builder, properties.ratingServiceUrl(), properties);
    }

    /**
     * @param delay demo switch: let the rating agency answer only after the read timeout of this client
     */
    public void requestRating(String partnerId, boolean delay) {
        restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/ratings/{partnerId}");
                    if (delay) {
                        uriBuilder.queryParam("delay", true);
                    }
                    return uriBuilder.build(partnerId);
                })
                .retrieve()
                .toBodilessEntity();
    }
}
