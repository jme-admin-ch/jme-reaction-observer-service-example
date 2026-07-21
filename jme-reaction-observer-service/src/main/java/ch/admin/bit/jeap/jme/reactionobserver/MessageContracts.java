package ch.admin.bit.jeap.jme.reactionobserver;

import ch.admin.bit.jeap.messaging.annotations.JeapMessageConsumerContract;
import ch.admin.bit.jeap.reaction.observer.event.identified.v2.ReactionIdentifiedEvent;
import ch.admin.bit.jeap.reaction.observer.event.observed.ReactionsObservedEvent;

@JeapMessageConsumerContract(value = ReactionIdentifiedEvent.TypeRef.class, topic = "jme-reaction-identified")
@JeapMessageConsumerContract(value = ReactionsObservedEvent.TypeRef.class, topic = "jme-reactions-observed")
public interface MessageContracts {
}
