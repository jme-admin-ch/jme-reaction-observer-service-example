package ch.admin.bit.jeap.jme.reactionobserver.headeronly;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/products")
class ProductController {

    @Operation(summary = "Get a product of the catalog (every product id exists)")
    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable String productId) {
        log.debug("GET /api/products/{} called", productId);
        return new ProductResponse(productId, "Product " + productId);
    }

    record ProductResponse(String productId, String name) {
    }
}
