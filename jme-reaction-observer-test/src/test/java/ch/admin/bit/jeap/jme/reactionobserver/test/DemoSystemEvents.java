package ch.admin.bit.jeap.jme.reactionobserver.test;

import ch.admin.bit.jeap.jme.reactionobserver.event.credit.reserved.JmeRosCreditReservedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckedEvent;
import ch.admin.bit.jeap.messaging.kafka.test.TestKafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static ch.admin.bit.jeap.messaging.kafka.test.KafkaTestConstants.TEST_CONSUMER_DISABLE_CONTRACT_CHECK_PROPERTY;

/**
 * Reads the business events of the demo system that no service of the demo system consumes, correlated by order id.
 * The test plays their consumer; every test run starts with a new broker, so the topics contain only the events of
 * the current run.
 */
@Component
class DemoSystemEvents {

    private static final String READ_FROM_BEGINNING = "auto.offset.reset=earliest";

    private final Map<String, JmeRosOrderCheckResult> orderCheckResults = new ConcurrentHashMap<>();
    private final Set<String> ordersWithReservedCredit = ConcurrentHashMap.newKeySet();

    @TestKafkaListener(topics = JmeRosOrderCheckedEvent.TypeRef.DEFAULT_TOPIC,
            properties = {TEST_CONSUMER_DISABLE_CONTRACT_CHECK_PROPERTY, READ_FROM_BEGINNING})
    void onOrderChecked(JmeRosOrderCheckedEvent event) {
        orderCheckResults.put(event.getReferences().getOrder().getOrderId(), event.getPayload().getResult());
    }

    @TestKafkaListener(topics = JmeRosCreditReservedEvent.TypeRef.DEFAULT_TOPIC,
            properties = {TEST_CONSUMER_DISABLE_CONTRACT_CHECK_PROPERTY, READ_FROM_BEGINNING})
    void onCreditReserved(JmeRosCreditReservedEvent event) {
        ordersWithReservedCredit.add(event.getReferences().getOrder().getOrderId());
    }

    Optional<JmeRosOrderCheckResult> orderCheckResult(String orderId) {
        return Optional.ofNullable(orderCheckResults.get(orderId));
    }

    boolean creditReserved(String orderId) {
        return ordersWithReservedCredit.contains(orderId);
    }
}
