package group.flyfish.dev.common.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JacksonConfigTest {

    @Test
    void configuresJackson3MapperForFrontendSafeJson() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().flyfishJsonMapperBuilderCustomizer().customize(builder);
        JsonMapper mapper = builder.build();

        Payload payload = new Payload(9_007_199_254_740_992L,
                LocalDateTime.of(2026, 5, 8, 16, 30, 0), Status.ONLINE);

        assertEquals(
                "{\"id\":\"9007199254740992\",\"time\":\"2026-05-08 16:30:00\",\"status\":\"ONLINE\"}",
                mapper.writeValueAsString(payload));
        assertNull(mapper.readValue("{\"status\":\"UNKNOWN\"}", StatusPayload.class).status());
    }

    private record Payload(Long id, LocalDateTime time, Status status) {
    }

    private record StatusPayload(Status status) {
    }

    private enum Status {
        ONLINE
    }
}
