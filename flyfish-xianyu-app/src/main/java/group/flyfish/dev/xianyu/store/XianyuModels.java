package group.flyfish.dev.xianyu.store;

import java.util.List;
import java.util.Map;

public final class XianyuModels {
    private XianyuModels() { }
    public record Seller(String key, String application, String tokenCipher, long expiresAt, boolean enabled) {
        @Override public String toString() { return "Seller[" + key + "]"; }
    }
    public record Capability(String seller, String operation, boolean granted, boolean verified,
                             long expiresAt, String requestKey, List<String> topics, List<String> categoryIds, String application) {
        public Capability {
            if(application==null || application.isBlank()) application="commerce";
        }
    }
    public record Listing(String key, String seller, String localItemId, String remoteItemId, long revision,
                          String status, boolean nativeEnabled, Map<String, Object> payload,
                          Map<String, Object> remoteSnapshot) { }
    public record Order(String key, String seller, String localOrderNo, String remoteOrderId, String buyerSubject,
                        String channel, long amountCents, String creationState, int platformState,
                        String refundState, long refundCents, String fulfillmentState, String shipmentState,
                        Map<String, Object> snapshot, long revision, long expiresAt) {
        public boolean frozen() { return refundCents>0 || !List.of("NONE", "CLOSED", "REJECTED").contains(refundState); }
        public boolean paid() { return platformState >= 2 && platformState <= 5; }
    }
    public record Job(String key, String seller, String kind, Map<String, Object> payload, String status,
                      int attempts, long nextAt, long leaseUntil, String leaseToken, String lastError) { }
    public record Identity(String subject, String application, String tokenCipher, String portalTokenCipher,
                           long expiresAt) {
        @Override public String toString() { return "Identity[redacted]"; }
    }
}
