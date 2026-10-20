package group.flyfish.dev.common.bridge;

public record BridgeRequest(String path, long timestamp, String nonce, String body, String signature) {
    @Override
    public String toString() {
        return "BridgeRequest[path=" + path + "]";
    }
}
