package ch.admin.bit.jeap.jme.reactionobserver.order.client;

import ch.admin.bit.jeap.jme.reactionobserver.order.OrderServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the credit reservation of the partner service (jme-ros-partner-service).
 */
@Component
public class CreditReservationClient {

    private final RestClient restClient;

    CreditReservationClient(RestClient.Builder builder, OrderServiceProperties properties) {
        this.restClient = RestClients.create(builder, properties.partnerServiceUrl(), properties);
    }

    /**
     * Reserves the amount on the credit limit of the partner.
     *
     * @param fail demo switch: let the partner service fail with a rollback after it has written the reservation
     * @throws org.springframework.web.client.HttpClientErrorException.Conflict if the amount exceeds the credit limit
     */
    public void reserve(String partnerId, String orderId, int amount, boolean fail) {
        restClient.post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/partners/{partnerId}/credit-reservations");
                    if (fail) {
                        uriBuilder.queryParam("fail", true);
                    }
                    return uriBuilder.build(partnerId);
                })
                .body(new CreditReservationRequest(orderId, amount))
                .retrieve()
                .toBodilessEntity();
    }

    record CreditReservationRequest(String orderId, int amount) {
    }
}
