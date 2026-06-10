package group.flyfish.dev.oauth.config;

import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.http.FoundAction;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.SeeOtherAction;
import org.pac4j.core.exception.http.WithLocationAction;
import org.pac4j.springframework.context.SpringWebfluxWebContext;
import org.pac4j.springframework.http.SpringWebfluxHttpActionAdapter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 自定义http接口适配器
 *
 * @author wangyu
 */
public class CustomHttpActionAdapter extends SpringWebfluxHttpActionAdapter {

    /**
     * 用于处理认证错误信息
     *
     * @param action  请求
     * @param context 上下文
     * @return 结果
     */
    @Override
    public Mono<?> adapt(final HttpAction action, final WebContext context) {
        if (action != null) {
            var code = action.getCode();

            switch (code) {
                case 401:
                    return write(context, code, "<html><body><h1>unauthorized</h1><br /><a href=\"/\">Home</a></body></html>");
                case 403:
                    return write(context, code, "<html><body><h1>forbidden</h1><br /><a href=\"/\">Home</a></body></html>");
                case 500:
                    return write(context, code, "<html><body><h1>internal error</h1><br /><a href=\"/\">Home</a></body></html>");
            }
        }

        // 读取token
        return super.adapt(mapToWrappedAction(action), context);
    }

    protected Mono<?> write(final WebContext context, final int code, final String content) {
        context.setResponseContentType("text/html;charset=UTF-8");
        final var response = ((SpringWebfluxWebContext) context).getNativeResponse();
        response.setRawStatusCode(code);
        final DataBuffer data = response.bufferFactory().wrap(content.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(data));
    }


    private HttpAction replaceAction(HttpAction action, String wrapped) {
        if (action instanceof FoundAction) {
            return new FoundAction(wrapped);
        } else if (action instanceof SeeOtherAction) {
            return new SeeOtherAction(wrapped);
        }
        return action;
    }

    private HttpAction mapToWrappedAction(HttpAction action) {
        if (!(action instanceof WithLocationAction locationAction)) {
            return action;
        }
        String target = locationAction.getLocation();
        if (StringUtils.isBlank(target)) {
            return action;
        }
        if (target.contains("authorize")) {
            return action;
        }
        String url = "/oauth/redirect?redirect=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        return replaceAction(action, url);
    }
}
