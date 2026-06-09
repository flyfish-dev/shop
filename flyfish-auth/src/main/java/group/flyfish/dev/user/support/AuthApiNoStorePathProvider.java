package group.flyfish.dev.user.support;

import group.flyfish.dev.common.http.ApiNoStorePathProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthApiNoStorePathProvider implements ApiNoStorePathProvider {

    @Override
    public List<String> prefixes() {
        return List.of("/oauth/", "/wx/", "/email/");
    }
}
