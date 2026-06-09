package group.flyfish.dev.platform.portal.support;

import group.flyfish.dev.common.http.ApiNoStorePathProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PortalApiNoStorePathProvider implements ApiNoStorePathProvider {

    @Override
    public List<String> prefixes() {
        return List.of("/portal/");
    }
}
