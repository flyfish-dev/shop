package group.flyfish.dev.lowcode.portal.support;

import group.flyfish.dev.common.http.ApiNoStorePathProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LowcodeApiNoStorePathProvider implements ApiNoStorePathProvider {

    @Override
    public List<String> prefixes() {
        return List.of("/integrity/");
    }
}
