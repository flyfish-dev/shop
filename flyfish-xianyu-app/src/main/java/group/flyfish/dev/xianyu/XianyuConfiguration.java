package group.flyfish.dev.xianyu;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class XianyuConfiguration {
    @Bean
    public org.springframework.beans.factory.InitializingBean xianyuDeploymentChecks(XianyuProperties properties,io.r2dbc.spi.ConnectionFactory database) {
        return () -> {
            if(properties.isTowerDeployment() && database.getMetadata().getName().equalsIgnoreCase("H2"))
                throw new IllegalStateException("Tower deployment requires a persistent approved database");
            if(properties.isSalesEnabled() || properties.isProcessingEnabled()) {
                properties.requireInfrastructure();
                byte[] key=java.util.Base64.getDecoder().decode(properties.getEncryptionKey()==null ? "" : properties.getEncryptionKey());
                if(key.length!=32 || properties.getBridgeSecret()==null || properties.getBridgeSecret().length()<32)
                    throw new IllegalStateException("Channel encryption and bridge keys must be configured before processing");
            }
        };
    }
    @Bean
    public org.springframework.web.reactive.function.client.WebClient.Builder xianyuWebClientBuilder(tools.jackson.databind.json.JsonMapper mapper) {
        return org.springframework.web.reactive.function.client.WebClient.builder().codecs(codecs -> {
            codecs.defaultCodecs().maxInMemorySize(12*1024*1024);
            codecs.defaultCodecs().jacksonJsonDecoder(new org.springframework.http.codec.json.JacksonJsonDecoder(mapper));
            codecs.defaultCodecs().jacksonJsonEncoder(new org.springframework.http.codec.json.JacksonJsonEncoder(mapper));
        });
    }
    @Bean
    public org.springframework.web.reactive.config.WebFluxConfigurer xianyuCors(XianyuProperties properties) {
        return new org.springframework.web.reactive.config.WebFluxConfigurer() {
            @Override public void addCorsMappings(org.springframework.web.reactive.config.CorsRegistry registry) {
                if(properties.getAllowedOrigins().contains("*")) throw new IllegalStateException("Explicit miniapp origins are required");
                if(!properties.getAllowedOrigins().isEmpty()) registry.addMapping("/**")
                        .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET","POST","OPTIONS").allowedHeaders("Authorization","Content-Type","Accept-Language","Idempotency-Key")
                        .exposedHeaders("Content-Disposition").allowCredentials(true);
            }
        };
    }
    @Bean
    public TransactionalOperator xianyuTransactions(ReactiveTransactionManager manager) {
        return TransactionalOperator.create(manager);
    }
}
