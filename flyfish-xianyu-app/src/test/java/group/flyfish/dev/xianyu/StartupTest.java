package group.flyfish.dev.xianyu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.r2dbc.url=r2dbc:h2:mem:///xianyu_startup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "flyfish.auth.client.base-url=http://127.0.0.1:1"})
class StartupTest {
    @Autowired Environment environment;
    @Test void startsWithSalesDisabledAndRejectsUnauthenticatedOrders() {
        WebTestClient client=WebTestClient.bindToServer().baseUrl("http://127.0.0.1:"+environment.getProperty("local.server.port")).build();
        client.get().uri("/shops/payment-methods").exchange().expectStatus().isOk().expectBody().jsonPath("$.result[0]").isEqualTo("xianyu");
        client.get().uri("/shops/orders/mine").header("Authorization","Bearer xy.invalid").exchange().expectStatus().isUnauthorized();
    }
}
