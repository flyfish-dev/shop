package group.flyfish.dev.shop.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeliveryResult {

    private boolean success;

    private String message;

    public static DeliveryResult ok(String message) {
        return new DeliveryResult(true, message);
    }

    public static DeliveryResult failed(String message) {
        return new DeliveryResult(false, message);
    }
}
