package group.flyfish.dev.shop.config;

import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.shop.git.GitRepositoryAccessResolver;
import group.flyfish.dev.shop.service.ShopDeliveryService;
import group.flyfish.dev.shop.service.impl.CompositeShopDeliveryService;
import group.flyfish.dev.shop.service.impl.DigitalDownloadDeliveryService;
import group.flyfish.dev.shop.service.impl.GitRepositoryDeliveryService;
import group.flyfish.dev.shop.service.impl.LicenseDeliveryService;
import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

@Configuration
@Import(H5ZhiFuConfig.class)
public class ShopConfig {

    @Bean
    public ShopDeliveryService shopDeliveryService(GiteaRepositoryClient giteaRepositoryClient,
                                                   GithubRepositoryClient githubRepositoryClient,
                                                   GiteeRepositoryClient giteeRepositoryClient,
                                                   GitAccessTokenService tokenService,
                                                   GitRepositoryAccessResolver repositoryAccessResolver,
                                                   DigitalDownloadDeliveryService digitalDownloadDeliveryService,
                                                   LicenseDeliveryService licenseDeliveryService) {
        GitRepositoryDeliveryService gitRepositoryDeliveryService = new GitRepositoryDeliveryService(
                giteaRepositoryClient, githubRepositoryClient, giteeRepositoryClient, tokenService,
                repositoryAccessResolver);
        return new CompositeShopDeliveryService(List.of(
                gitRepositoryDeliveryService,
                digitalDownloadDeliveryService,
                licenseDeliveryService
        ));
    }
}
