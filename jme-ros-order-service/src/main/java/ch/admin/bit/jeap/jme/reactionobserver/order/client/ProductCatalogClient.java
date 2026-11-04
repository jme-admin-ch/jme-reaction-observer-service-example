package ch.admin.bit.jeap.jme.reactionobserver.order.client;

import ch.admin.bit.jeap.jme.reactionobserver.order.OrderServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the product catalog (jme-ros-header-only).
 */
@Component
public class ProductCatalogClient {

    private final RestClient restClient;

    ProductCatalogClient(RestClient.Builder builder, OrderServiceProperties properties) {
        this.restClient = RestClients.create(builder, properties.productCatalogUrl(), properties);
    }

    public void checkProduct(String productId) {
        restClient.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .toBodilessEntity();
    }
}
