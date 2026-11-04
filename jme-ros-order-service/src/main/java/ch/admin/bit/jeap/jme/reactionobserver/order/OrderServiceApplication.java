package ch.admin.bit.jeap.jme.reactionobserver.order;

import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedEvent;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.messaging.annotations.JeapMessageProducerContract;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Consumer of the reaction observer demo system. It publishes the order submitted event, consumes it itself (a chain
 * that starts and ends at the same component), checks the order by calling the other services of the demo system and
 * publishes the order checked event.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@JeapMessageProducerContract(JmeRosOrderSubmittedEvent.TypeRef.class)
@JeapMessageConsumerContract(JmeRosOrderSubmittedEvent.TypeRef.class)
@JeapMessageProducerContract(JmeRosOrderCheckedEvent.TypeRef.class)
public class OrderServiceApplication {

    protected OrderServiceApplication() {
    }

    static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
