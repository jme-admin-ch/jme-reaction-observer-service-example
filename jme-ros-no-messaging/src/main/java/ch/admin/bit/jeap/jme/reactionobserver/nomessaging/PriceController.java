package ch.admin.bit.jeap.jme.reactionobserver.nomessaging;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/prices")
class PriceController {

    /**
     * Unit price in whole CHF of every product.
     */
    static final int UNIT_PRICE = 100;

    private final RestClient productCatalog;

    /**
     * The client is built from the builder provided by Spring Boot: only then do the customizers registered by
     * libraries (e.g. the jEAP Reaction Observer Library) apply to the client.
     */
    PriceController(RestClient.Builder builder,
                    @Value("${jme.ros.no-messaging.product-catalog-url}") String productCatalogUrl) {
        this.productCatalog = builder.baseUrl(productCatalogUrl).build();
    }

    @Operation(summary = "Get the unit price of a product; checks the product in the catalog first")
    @GetMapping("/{productId}")
    public PriceResponse getPrice(@PathVariable String productId) {
        productCatalog.get()
                .uri("/api/products/{productId}", productId)
                .retrieve()
                .toBodilessEntity();
        return new PriceResponse(productId, UNIT_PRICE);
    }

    record PriceResponse(String productId, int unitPrice) {
    }
}
