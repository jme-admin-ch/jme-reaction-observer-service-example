package ch.admin.bit.jeap.jme.reactionobserver.partner;

import ch.admin.bit.jeap.jme.reactionobserver.event.credit.reserved.JmeRosCreditReservedEvent;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContract;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Provider of the reaction observer demo system: a partner service using the jEAP Reaction Observer Library. It
 * reserves credit for orders and publishes the credit reserved event over the transactional outbox.
 * <p>
 * The transactional outbox registers its own JPA repositories and entities, which turns off the default scanning of
 * Spring Boot; the repositories and entities of this service are therefore enabled explicitly.
 */
@SpringBootApplication
@EntityScan
@EnableJpaRepositories
@JeapMessageProducerContract(JmeRosCreditReservedEvent.TypeRef.class)
public class PartnerServiceApplication {

    protected PartnerServiceApplication() {
    }

    static void main(String[] args) {
        SpringApplication.run(PartnerServiceApplication.class, args);
    }
}
