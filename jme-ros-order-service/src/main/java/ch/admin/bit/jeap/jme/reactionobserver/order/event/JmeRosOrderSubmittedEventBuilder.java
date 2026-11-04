package ch.admin.bit.jeap.jme.reactionobserver.order.event;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedPayload;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.submitted.JmeRosOrderSubmittedReferences;
import ch.admin.bit.jeap.jme.reactionobserver.order.api.OrderRequest;

class JmeRosOrderSubmittedEventBuilder
        extends AvroDomainEventBuilder<JmeRosOrderSubmittedEventBuilder, JmeRosOrderSubmittedEvent> {

    private final String serviceName;
    private String orderId;
    private OrderRequest order;

    private JmeRosOrderSubmittedEventBuilder(String serviceName) {
        super(JmeRosOrderSubmittedEvent::new);
        this.serviceName = serviceName;
    }

    static JmeRosOrderSubmittedEventBuilder create(String serviceName) {
        return new JmeRosOrderSubmittedEventBuilder(serviceName);
    }

    JmeRosOrderSubmittedEventBuilder order(String orderId, OrderRequest order) {
        this.orderId = orderId;
        this.order = order;
        return this;
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }

    @Override
    protected String getSystemName() {
        return OrderEventPublisher.SYSTEM_NAME;
    }

    @Override
    protected JmeRosOrderSubmittedEventBuilder self() {
        return this;
    }

    @Override
    public JmeRosOrderSubmittedEvent build() {
        setReferences(JmeRosOrderSubmittedReferences.newBuilder()
                .setOrder(OrderEventPublisher.orderReference(orderId))
                .build());
        setPayload(JmeRosOrderSubmittedPayload.newBuilder()
                .setPartnerId(order.partnerId())
                .setProductId(order.productId())
                .setQuantity(order.quantity())
                .setSimulateSlowRating(order.simulateSlowRating())
                .setSimulateFailedReservation(order.simulateFailedReservation())
                .build());
        setProcessId(orderId);
        return super.build();
    }
}
