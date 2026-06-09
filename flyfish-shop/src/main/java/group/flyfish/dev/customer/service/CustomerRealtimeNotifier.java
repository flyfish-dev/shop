package group.flyfish.dev.customer.service;

import group.flyfish.dev.customer.domain.po.CustomerConversation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class CustomerRealtimeNotifier {

    private final Sinks.Many<CustomerRealtimeEvent> sink = Sinks.many().multicast().directBestEffort();

    public Flux<CustomerRealtimeEvent> events() {
        return sink.asFlux();
    }

    public void conversationChanged(CustomerConversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return;
        }
        sink.tryEmitNext(CustomerRealtimeEvent.conversation(conversation.getId(), conversation.getUserId()));
    }

    public void ticketsChanged() {
        sink.tryEmitNext(CustomerRealtimeEvent.allUsers());
    }
}
