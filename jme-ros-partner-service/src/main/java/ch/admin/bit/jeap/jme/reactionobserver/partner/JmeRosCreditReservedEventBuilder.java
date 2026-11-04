package ch.admin.bit.jeap.jme.reactionobserver.partner;

import ch.admin.bit.jeap.domainevent.avro.AvroDomainEventBuilder;
import ch.admin.bit.jeap.jme.reactionobserver.event.credit.reserved.JmeRosCreditReservedEvent;
import ch.admin.bit.jeap.jme.reactionobserver.event.credit.reserved.JmeRosCreditReservedPayload;
import ch.admin.bit.jeap.jme.reactionobserver.event.credit.reserved.JmeRosCreditReservedReferences;
import ch.admin.bit.jme.messaging.OrderReference;

class JmeRosCreditReservedEventBuilder
        extends AvroDomainEventBuilder<JmeRosCreditReservedEventBuilder, JmeRosCreditReservedEvent> {

    private static final String SYSTEM_NAME = "JME";
    private static final String ORDER_REFERENCE_TYPE = "order";

    private final String serviceName;
    private CreditReservation reservation;

    private JmeRosCreditReservedEventBuilder(String serviceName) {
        super(JmeRosCreditReservedEvent::new);
        this.serviceName = serviceName;
    }

    static JmeRosCreditReservedEventBuilder create(String serviceName) {
        return new JmeRosCreditReservedEventBuilder(serviceName);
    }

    JmeRosCreditReservedEventBuilder reservation(CreditReservation reservation) {
        this.reservation = reservation;
        return this;
    }

    @Override
    protected String getServiceName() {
        return serviceName;
    }

    @Override
    protected String getSystemName() {
        return SYSTEM_NAME;
    }

    @Override
    protected JmeRosCreditReservedEventBuilder self() {
        return this;
    }

    @Override
    public JmeRosCreditReservedEvent build() {
        setReferences(JmeRosCreditReservedReferences.newBuilder()
                .setOrder(OrderReference.newBuilder()
                        .setType(ORDER_REFERENCE_TYPE)
                        .setOrderId(reservation.getOrderId())
                        .build())
                .build());
        setPayload(JmeRosCreditReservedPayload.newBuilder()
                .setReservationId(reservation.getId().toString())
                .setPartnerId(reservation.getPartnerId())
                .setAmount(reservation.getAmount())
                .build());
        setProcessId(reservation.getOrderId());
        return super.build();
    }
}
