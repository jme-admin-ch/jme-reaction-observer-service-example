package ch.admin.bit.jeap.jme.reactionobserver.partner;

import ch.admin.bit.jeap.jme.reactionobserver.event.credit.reserved.JmeRosCreditReservedEvent;
import ch.admin.bit.jeap.messaging.transactionaloutbox.outbox.TransactionalOutbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
class CreditReservationService {

    /**
     * Credit limit in CHF of every partner, per reservation (reservations are not summed up).
     */
    static final int CREDIT_LIMIT = 5000;

    private final CreditReservationRepository repository;
    private final TransactionalOutbox outbox;
    private final String serviceName;

    CreditReservationService(CreditReservationRepository repository, TransactionalOutbox outbox,
                             @Value("${jeap.messaging.kafka.service-name}") String serviceName) {
        this.repository = repository;
        this.outbox = outbox;
        this.serviceName = serviceName;
    }

    /**
     * Stores the reservation and its event in one transaction: the transactional outbox sends the event only after the
     * transaction has been committed.
     *
     * @param fail demo switch: fail after the reservation and its event have been written, which rolls both back
     */
    @Transactional
    public CreditReservation reserve(String partnerId, String orderId, int amount, boolean fail) {
        if (amount > CREDIT_LIMIT) {
            throw new CreditLimitExceededException(partnerId, amount);
        }
        CreditReservation reservation = repository.save(new CreditReservation(orderId, partnerId, amount));
        JmeRosCreditReservedEvent event = JmeRosCreditReservedEventBuilder.create(serviceName)
                .idempotenceId(reservation.getId().toString())
                .reservation(reservation)
                .build();
        outbox.sendMessage(event, JmeRosCreditReservedEvent.TypeRef.DEFAULT_TOPIC);
        if (fail) {
            throw new SimulatedReservationFailureException(orderId);
        }
        log.info("Reserved CHF {} for order {} of partner {}", amount, orderId, partnerId);
        return reservation;
    }
}
