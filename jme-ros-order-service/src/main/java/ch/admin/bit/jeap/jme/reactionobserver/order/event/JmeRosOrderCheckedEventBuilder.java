package ch.admin.bit.jeap.jme.reactionobserver.order.event;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckResult;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckedPayload;
import ch.admin.bit.jeap.jme.reactionobserver.event.order.checked.JmeRosOrderCheckedReferences;

class JmeRosOrderCheckedEventBuilder
        extends AvroDomainEventBuilder<JmeRosOrderCheckedEventBuilder, JmeRosOrderCheckedEvent> {

    private final String serviceName;
    private String orderId;
    private JmeRosOrderCheckResult result;

    private JmeRosOrderCheckedEventBuilder(String serviceName) {
        super(JmeRosOrderCheckedEvent::new);
        this.serviceName = serviceName;
    }

    static JmeRosOrderCheckedEventBuilder create(String serviceName) {
        return new JmeRosOrderCheckedEventBuilder(serviceName);
    }

    JmeRosOrderCheckedEventBuilder result(String orderId, JmeRosOrderCheckResult result) {
        this.orderId = orderId;
        this.result = result;
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
    protected JmeRosOrderCheckedEventBuilder self() {
        return this;
    }

    @Override
    public JmeRosOrderCheckedEvent build() {
        setReferences(JmeRosOrderCheckedReferences.newBuilder()
                .setOrder(OrderEventPublisher.orderReference(orderId))
                .build());
        setPayload(JmeRosOrderCheckedPayload.newBuilder()
                .setResult(result)
                .build());
        setProcessId(orderId);
        return super.build();
    }
}
