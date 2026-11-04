package ch.admin.bit.jeap.jme.reactionobserver.order.api;

import ch.admin.bit.jeap.jme.reactionobserver.order.event.OrderEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Start endpoint of the demo chain: accepts an order and publishes the order submitted event. The order is checked
 * asynchronously, by the listener on the order submitted event.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
class OrderController {

    private final OrderEventPublisher orderEventPublisher;

    @Operation(summary = "Submit an order; it is checked asynchronously")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse submitOrder(@Valid @RequestBody OrderRequest order) {
        String orderId = UUID.randomUUID().toString();
        orderEventPublisher.publishOrderSubmitted(orderId, order);
        log.info("Order {} submitted: {}", orderId, order);
        return new OrderResponse(orderId);
    }
}
