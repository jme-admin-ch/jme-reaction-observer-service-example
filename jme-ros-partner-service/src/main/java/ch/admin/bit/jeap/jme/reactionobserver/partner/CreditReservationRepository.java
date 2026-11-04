package ch.admin.bit.jeap.jme.reactionobserver.partner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CreditReservationRepository extends JpaRepository<CreditReservation, UUID> {
}
