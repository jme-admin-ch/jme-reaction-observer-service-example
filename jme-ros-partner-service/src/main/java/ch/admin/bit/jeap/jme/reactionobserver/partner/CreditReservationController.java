package ch.admin.bit.jeap.jme.reactionobserver.partner;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/partners/{partnerId}/credit-reservations")
@RequiredArgsConstructor
class CreditReservationController {

    private final CreditReservationService creditReservationService;

    @Operation(summary = "Reserve an amount on the credit limit of a partner (CHF 5000 per reservation)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditReservationResponse reserve(@PathVariable String partnerId,
                                             @Valid @RequestBody CreditReservationRequest request,
                                             @Parameter(description = "Demo switch: fail with a rollback after writing the reservation and its event")
                                             @RequestParam(defaultValue = "false") boolean fail) {
        CreditReservation reservation = creditReservationService.reserve(partnerId, request.orderId(), request.amount(), fail);
        return new CreditReservationResponse(reservation.getId().toString());
    }

    record CreditReservationRequest(
            @Schema(example = "4f6c1e0a-0c1d-4d2b-9a57-0e6b1d2f3a4b") @NotBlank String orderId,
            @Schema(description = "Amount in whole CHF", example = "1200") @Positive int amount) {
    }

    record CreditReservationResponse(String reservationId) {
    }
}
