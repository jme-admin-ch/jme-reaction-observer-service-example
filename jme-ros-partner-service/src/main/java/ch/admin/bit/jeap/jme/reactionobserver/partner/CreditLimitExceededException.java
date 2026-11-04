package ch.admin.bit.jeap.jme.reactionobserver.partner;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The amount exceeds the credit limit of the partner: the business result of a correct processing, answered with 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
class CreditLimitExceededException extends RuntimeException {

    CreditLimitExceededException(String partnerId, int amount) {
        super("Amount of CHF " + amount + " exceeds the credit limit of partner " + partnerId);
    }
}
