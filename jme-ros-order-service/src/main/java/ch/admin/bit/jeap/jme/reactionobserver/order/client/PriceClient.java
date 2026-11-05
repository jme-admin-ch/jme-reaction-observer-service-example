package ch.admin.bit.jeap.jme.reactionobserver.order.client;

import ch.admin.bit.jeap.jme.reactionobserver.order.OrderServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the price service (jme-ros-no-messaging).
 */
@Component
public class PriceClient {

    private final RestClient restClient;

    PriceClient(RestClient.Builder builder, OrderServiceProperties properties) {
        this.restClient = RestClients.create(builder, properties.priceServiceUrl(), properties);
    }

    /**
     * @return the unit price of the product in whole CHF
     */
    public int getUnitPrice(String productId) {
        PriceResponse price = restClient.get()
                .uri("/api/prices/{productId}", productId)
                .retrieve()
                .body(PriceResponse.class);
        if (price == null) {
            throw new RestClientException("No price returned for product " + productId);
        }
        return price.unitPrice();
    }

    record PriceResponse(String productId, int unitPrice) {
    }
}
