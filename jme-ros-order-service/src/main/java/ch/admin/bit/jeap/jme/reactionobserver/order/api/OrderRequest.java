package ch.admin.bit.jeap.jme.reactionobserver.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * An order to be checked by the order service.
 *
 * @param partnerId                 the ordering partner
 * @param productId                 the ordered product
 * @param quantity                  the ordered quantity, from 1 to {@value #MAX_QUANTITY}; together with the unit price
 *                                  of CHF 100 it determines the amount (credit rating from CHF 1000, credit limit
 *                                  CHF 5000). The upper bound keeps the amount far within the range of the integer
 *                                  amounts of the API and the events.
 * @param simulateSlowRating        demo switch: the external credit rating answers only after the timeout
 * @param simulateFailedReservation demo switch: the credit reservation fails with a rollback after the reservation
 *                                  and its event have been written
 */
public record OrderRequest(
        @Schema(example = "P1") @NotBlank String partnerId,
        @Schema(example = "X1") @NotBlank String productId,
        @Schema(example = "12", minimum = "1", maximum = "1000") @Min(1) @Max(MAX_QUANTITY) int quantity,
        @Schema(example = "false") boolean simulateSlowRating,
        @Schema(example = "false") boolean simulateFailedReservation) {

    public static final int MAX_QUANTITY = 1000;
}
