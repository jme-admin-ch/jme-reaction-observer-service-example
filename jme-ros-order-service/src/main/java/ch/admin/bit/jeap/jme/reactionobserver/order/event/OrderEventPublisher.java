package ch.admin.bit.jeap.jme.reactionobserver.order.event;

import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.order.api.OrderRequest;
import ch.admin.bit.jeap.messaging.avro.AvroMessage;
import ch.admin.bit.jeap.messaging.avro.AvroMessageKey;
import ch.admin.bit.jme.messaging.OrderReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes the order events directly to Kafka (the order service has no database and thus no transactional outbox).
 */
@Component
public class OrderEventPublisher {

    static final String SYSTEM_NAME = "JME";
    private static final String ORDER_REFERENCE_TYPE = "order";
    private static final int SEND_TIMEOUT_SECONDS = 30;

    private final KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate;
    private final String serviceName;

    OrderEventPublisher(KafkaTemplate<AvroMessageKey, AvroMessage> kafkaTemplate,
                        @Value("${jeap.messaging.kafka.service-name}") String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.serviceName = serviceName;
    }

    public void publishOrderSubmitted(String orderId, OrderRequest order) {
        JmeRosOrderSubmittedEvent event = JmeRosOrderSubmittedEventBuilder.create(serviceName)
                .idempotenceId(orderId)
                .order(orderId, order)
                .build();
        send(JmeRosOrderSubmittedEvent.TypeRef.DEFAULT_TOPIC, event);
    }

    public void publishOrderChecked(String orderId, JmeRosOrderCheckResult result) {
        JmeRosOrderCheckedEvent event = JmeRosOrderCheckedEventBuilder.create(serviceName)
                .idempotenceId(orderId)
                .result(orderId, result)
                .build();
        send(JmeRosOrderCheckedEvent.TypeRef.DEFAULT_TOPIC, event);
    }

    static OrderReference orderReference(String orderId) {
        return OrderReference.newBuilder()
                .setType(ORDER_REFERENCE_TYPE)
                .setOrderId(orderId)
                .build();
    }

    private void send(String topic, AvroMessage event) {
        try {
            kafkaTemplate.send(topic, event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublicationException("Publishing to " + topic + " failed", e);
        }
    }
}
