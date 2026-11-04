package ch.admin.bit.jeap.jme.reactionobserver.order.api;

/**
 * @param orderId id of the accepted order, referenced by the order events
 */
public record OrderResponse(String orderId) {
}
