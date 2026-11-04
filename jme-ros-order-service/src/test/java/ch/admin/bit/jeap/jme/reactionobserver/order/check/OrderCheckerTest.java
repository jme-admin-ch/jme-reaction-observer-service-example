package ch.admin.bit.jeap.jme.reactionobserver.order.check;

import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.CreditReservationClient;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.PriceClient;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.ProductCatalogClient;
import ch.admin.bit.jeap.jme.reactionobserver.order.client.RatingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCheckerTest {

    private static final String ORDER_ID = "order-1";
    private static final String PARTNER_ID = "P1";
    private static final String PRODUCT_ID = "X1";
    private static final int UNIT_PRICE = 100;

    private final ProductCatalogClient productCatalogClient = mock(ProductCatalogClient.class);
    private final PriceClient priceClient = mock(PriceClient.class);
    private final RatingClient ratingClient = mock(RatingClient.class);
    private final CreditReservationClient creditReservationClient = mock(CreditReservationClient.class);
    private final OrderChecker orderChecker =
            new OrderChecker(productCatalogClient, priceClient, ratingClient, creditReservationClient);

    @BeforeEach
    void setUp() {
        when(priceClient.getUnitPrice(PRODUCT_ID)).thenReturn(UNIT_PRICE);
    }

    @Test
    void acceptsOrderBelowRatingThresholdWithoutRatingCredit() {
        JmeRosOrderCheckResult result = orderChecker.check(order(9, false, false));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.ACCEPTED);
        verify(productCatalogClient).checkProduct(PRODUCT_ID);
        verify(ratingClient, never()).requestRating(anyString(), anyBoolean());
        verify(creditReservationClient).reserve(PARTNER_ID, ORDER_ID, 900, false);
    }

    @Test
    void acceptsOrderFromRatingThresholdAfterRatingCredit() {
        JmeRosOrderCheckResult result = orderChecker.check(order(10, false, false));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.ACCEPTED);
        verify(ratingClient).requestRating(PARTNER_ID, false);
        verify(creditReservationClient).reserve(PARTNER_ID, ORDER_ID, 1000, false);
    }

    @Test
    void requiresReviewButStillReservesCreditIfRatingTimesOut() {
        doThrow(new ResourceAccessException("timeout")).when(ratingClient).requestRating(PARTNER_ID, true);

        JmeRosOrderCheckResult result = orderChecker.check(order(12, true, false));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.REVIEW_REQUIRED);
        verify(creditReservationClient).reserve(PARTNER_ID, ORDER_ID, 1200, false);
    }

    @Test
    void rejectsOrderIfCreditLimitIsExceeded() {
        doThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(creditReservationClient).reserve(PARTNER_ID, ORDER_ID, 6000, false);

        JmeRosOrderCheckResult result = orderChecker.check(order(60, false, false));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.REJECTED);
    }

    @Test
    void requiresReviewIfCreditReservationFails() {
        doThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Error", null, null, null))
                .when(creditReservationClient).reserve(PARTNER_ID, ORDER_ID, 1200, true);

        JmeRosOrderCheckResult result = orderChecker.check(order(12, false, true));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.REVIEW_REQUIRED);
    }

    @Test
    void requiresReviewButStillReservesCreditIfProductCannotBeChecked() {
        doThrow(new ResourceAccessException("down")).when(productCatalogClient).checkProduct(PRODUCT_ID);

        JmeRosOrderCheckResult result = orderChecker.check(order(1, false, false));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.REVIEW_REQUIRED);
        verify(creditReservationClient).reserve(PARTNER_ID, ORDER_ID, 100, false);
    }

    @Test
    void requiresReviewWithoutReservingCreditIfThereIsNoPrice() {
        when(priceClient.getUnitPrice(PRODUCT_ID)).thenThrow(new ResourceAccessException("down"));

        JmeRosOrderCheckResult result = orderChecker.check(order(12, false, false));

        assertThat(result).isEqualTo(JmeRosOrderCheckResult.REVIEW_REQUIRED);
        verify(creditReservationClient, never()).reserve(anyString(), anyString(), anyInt(), anyBoolean());
    }

    private static SubmittedOrder order(int quantity, boolean slowRating, boolean failedReservation) {
        return new SubmittedOrder(ORDER_ID, PARTNER_ID, PRODUCT_ID, quantity, slowRating, failedReservation);
    }
}
