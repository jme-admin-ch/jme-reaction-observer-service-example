package ch.admin.bit.jeap.jme.reactionobserver.order.check;

import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedPayload;
import ch.admin.bit.jeap.jme.reactionobserver.order.event.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes the order submitted event published by this service itself: the order is accepted synchronously and
 * checked asynchronously. The demo system thereby also shows a chain that starts and ends at the same component.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderSubmittedEventListener {

    private final OrderChecker orderChecker;
    private final OrderEventPublisher orderEventPublisher;

    @KafkaListener(topics = JmeRosOrderSubmittedEvent.TypeRef.DEFAULT_TOPIC)
    public void onOrderSubmitted(JmeRosOrderSubmittedEvent event, Acknowledgment ack) {
        String orderId = event.getReferences().getOrder().getOrderId();
        JmeRosOrderSubmittedPayload payload = event.getPayload();
        log.info("Checking order {}", orderId);
        JmeRosOrderCheckResult result = orderChecker.check(new SubmittedOrder(orderId, payload.getPartnerId(),
                payload.getProductId(), payload.getQuantity(), payload.getSimulateSlowRating(),
                payload.getSimulateFailedReservation()));
        orderEventPublisher.publishOrderChecked(orderId, result);
        ack.acknowledge();
    }
}
