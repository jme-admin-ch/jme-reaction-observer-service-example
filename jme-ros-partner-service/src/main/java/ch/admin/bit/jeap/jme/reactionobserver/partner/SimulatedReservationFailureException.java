package ch.admin.bit.jeap.jme.reactionobserver.partner;

/**
 * Demo failure after the reservation and its event have been written. It is deliberately not handled, so that it
 * reaches the error dispatch of Spring Boot (500), like any unexpected technical failure.
 */
class SimulatedReservationFailureException extends RuntimeException {

    SimulatedReservationFailureException(String orderId) {
        super("Simulated failure after reserving credit for order " + orderId);
    }
}
