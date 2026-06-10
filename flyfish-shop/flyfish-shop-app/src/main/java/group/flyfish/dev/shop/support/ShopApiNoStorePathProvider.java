package group.flyfish.dev.shop.support;

import group.flyfish.dev.common.http.ApiNoStorePathProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShopApiNoStorePathProvider implements ApiNoStorePathProvider {

    @Override
    public List<String> prefixes() {
        return List.of("/shops/");
    }
}
