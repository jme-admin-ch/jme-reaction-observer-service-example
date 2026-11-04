package ch.admin.bit.jeap.jme.reactionobserver.partner;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CreditReservation {

    @Id
    private UUID id;
    private String orderId;
    private String partnerId;
    private int amount;
    private Instant createdAt;

    CreditReservation(String orderId, String partnerId, int amount) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.partnerId = partnerId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }
}
