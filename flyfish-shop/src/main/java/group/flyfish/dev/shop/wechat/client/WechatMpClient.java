package group.flyfish.dev.shop.wechat.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

/**
 * 微信公众号 HTTP Interface Client。
 */
@HttpExchange
public interface WechatMpClient {

    @GetExchange("/cgi-bin/token")
    Mono<WechatAccessTokenResponse> getAccessToken(@RequestParam("grant_type") String grantType,
                                                   @RequestParam("appid") String appId,
                                                   @RequestParam("secret") String secret);

    @PostExchange("/cgi-bin/qrcode/create")
    Mono<WechatQrCodeTicketResponse> createQrCode(@RequestParam("access_token") String accessToken,
                                                  @RequestBody WechatQrCodeRequest request);

    @GetExchange("/cgi-bin/user/info")
    Mono<WechatUserInfoResponse> getUserInfo(@RequestParam("access_token") String accessToken,
                                             @RequestParam("openid") String openid,
                                             @RequestParam("lang") String lang);

    @GetExchange("/cgi-bin/customservice/getonlinekflist")
    Mono<WechatKfOnlineListResponse> listOnlineKefu(@RequestParam("access_token") String accessToken);

    @PostExchange("/cgi-bin/message/custom/send")
    Mono<WechatSimpleApiResponse> sendCustomMessage(@RequestParam("access_token") String accessToken,
                                                    @RequestBody WechatCustomMessageRequest request);
}
