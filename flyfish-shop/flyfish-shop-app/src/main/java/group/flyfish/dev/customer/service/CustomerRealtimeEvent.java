package group.flyfish.dev.customer.service;

public record CustomerRealtimeEvent(Long conversationId, Long userId, boolean broadcast) {

    public static CustomerRealtimeEvent conversation(Long conversationId, Long userId) {
        return new CustomerRealtimeEvent(conversationId, userId, false);
    }

    public static CustomerRealtimeEvent allUsers() {
        return new CustomerRealtimeEvent(null, null, true);
    }
}
