package ch.admin.bit.jeap.jme.reactionobserver.order.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, OrderRequest.MAX_QUANTITY})
    void acceptsQuantityWithinRange(int quantity) {
        assertThat(validator.validate(order(quantity))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, OrderRequest.MAX_QUANTITY + 1, 42949673})
    void rejectsQuantityOutsideRange(int quantity) {
        assertThat(validator.validate(order(quantity))).hasSize(1);
    }

    private static OrderRequest order(int quantity) {
        return new OrderRequest("P1", "X1", quantity, false, false);
    }
}
