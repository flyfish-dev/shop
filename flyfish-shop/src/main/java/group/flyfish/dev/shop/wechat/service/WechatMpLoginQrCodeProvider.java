package group.flyfish.dev.shop.wechat.service;

import group.flyfish.dev.wechat.bean.WechatLoginDto;
import group.flyfish.dev.wechat.contants.WxScanScenes;
import group.flyfish.dev.wechat.service.WechatLoginQrCodeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 基于微信公众号 API 的登录二维码提供器。
 */
@Service
@RequiredArgsConstructor
public class WechatMpLoginQrCodeProvider implements WechatLoginQrCodeProvider {

    private final WechatMpApiService wechatMpApiService;

    @Override
    public Mono<WechatLoginDto> createLoginQrCode(String scene) {
        return wechatMpApiService.createTemporaryQrCode(WxScanScenes.LOGIN + scene, WxScanScenes.QR_CODE_EXPIRE)
                .map(ticket -> WechatLoginDto.of(false,
                        wechatMpApiService.qrCodePictureUrl(ticket.getTicket()), scene));
    }
}
