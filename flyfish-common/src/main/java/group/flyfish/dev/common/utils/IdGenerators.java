package group.flyfish.dev.common.utils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class IdGenerators {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private IdGenerators() {
    }

    public static String idString() {
        long timestamp = System.currentTimeMillis() << 12;
        long sequence = SEQUENCE.getAndIncrement() & 0xFFF;
        return Long.toString(timestamp | sequence);
    }

    public static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
