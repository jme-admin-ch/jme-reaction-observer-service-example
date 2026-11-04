package ch.admin.bit.jeap.jme.reactionobserver.order.check;

import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.CreditReservationClient;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.PriceClient;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.ProductCatalogClient;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.RatingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.OptionalInt;

/**
 * Checks an order by calling the other services of the demo system.
 * <p>
 * The product catalog, the price service and the credit reservation are always called, the external credit rating
 * only depends on the amount. The outcome of a call only changes the result of the check, never which calls are made:
 * a reaction is identified by its trigger and the set of its actions, so skipping a call after an error would create
 * a second reaction. The call to the external credit rating agency is the exception and depends on the amount: it
 * never becomes an action, because the agency never returns the header of the jEAP Reaction Observer. At most it is
 * counted as an unidentified action, which changes neither the set of actions nor the reaction. Every error of a call
 * is caught, so that the order checked event is published in any case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderChecker {

    /**
     * Amount in CHF from which the credit of the partner is rated by the external credit rating agency.
     */
    static final int RATING_THRESHOLD = 1000;

    private final ProductCatalogClient productCatalogClient;
    private final PriceClient priceClient;
    private final RatingClient ratingClient;
    private final CreditReservationClient creditReservationClient;

    public JmeRosOrderCheckResult check(SubmittedOrder order) {
        boolean productAvailable = checkProduct(order.productId());
        OptionalInt unitPrice = getUnitPrice(order.productId());
        if (unitPrice.isEmpty()) {
            // Without a price there is no amount to rate and to reserve. This only happens if the price service is
            // down, which is not one of the cases this demo system shows.
            return JmeRosOrderCheckResult.REVIEW_REQUIRED;
        }
        int amount = unitPrice.getAsInt() * order.quantity();
        boolean ratingAvailable = amount < RATING_THRESHOLD || rateCredit(order.partnerId(), order.simulateSlowRating());
        ReservationOutcome reservation = reserveCredit(order.partnerId(), order.orderId(), amount, order.simulateFailedReservation());
        JmeRosOrderCheckResult result = result(productAvailable, ratingAvailable, reservation);
        log.info("Order {} with amount CHF {} checked: {}", order.orderId(), amount, result);
        return result;
    }

    private boolean checkProduct(String productId) {
        try {
            productCatalogClient.checkProduct(productId);
            return true;
        } catch (RestClientException e) {
            log.warn("Product {} could not be checked: {}", productId, e.getMessage());
            return false;
        }
    }

    private OptionalInt getUnitPrice(String productId) {
        try {
            return OptionalInt.of(priceClient.getUnitPrice(productId));
        } catch (RestClientException e) {
            log.warn("No price for product {}: {}", productId, e.getMessage());
            return OptionalInt.empty();
        }
    }

    private boolean rateCredit(String partnerId, boolean slow) {
        try {
            ratingClient.requestRating(partnerId, slow);
            return true;
        } catch (RestClientException e) {
            log.warn("Credit of partner {} could not be rated: {}", partnerId, e.getMessage());
            return false;
        }
    }

    private ReservationOutcome reserveCredit(String partnerId, String orderId, int amount, boolean fail) {
        try {
            creditReservationClient.reserve(partnerId, orderId, amount, fail);
            return ReservationOutcome.RESERVED;
        } catch (HttpClientErrorException.Conflict e) {
            log.info("Credit limit of partner {} exceeded by order {}", partnerId, orderId);
            return ReservationOutcome.LIMIT_EXCEEDED;
        } catch (RestClientException e) {
            log.warn("Credit of partner {} could not be reserved: {}", partnerId, e.getMessage());
            return ReservationOutcome.FAILED;
        }
    }

    private static JmeRosOrderCheckResult result(boolean productAvailable, boolean ratingAvailable,
                                                 ReservationOutcome reservation) {
        if (reservation == ReservationOutcome.LIMIT_EXCEEDED) {
            return JmeRosOrderCheckResult.REJECTED;
        }
        if (productAvailable && ratingAvailable && reservation == ReservationOutcome.RESERVED) {
            return JmeRosOrderCheckResult.ACCEPTED;
        }
        return JmeRosOrderCheckResult.REVIEW_REQUIRED;
    }

    private enum ReservationOutcome {
        RESERVED, LIMIT_EXCEEDED, FAILED
    }
}
