package ch.admin.bit.jeap.jme.reactionobserver.order.check;

/**
 * An order to be checked, as submitted with the order submitted event.
 */
public record SubmittedOrder(
        String orderId,
        String partnerId,
        String productId,
        int quantity,
        boolean simulateSlowRating,
        boolean simulateFailedReservation) {
}
