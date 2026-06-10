package group.flyfish.dev.shop.service.support.h5zhifu;

import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuResultDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

/**
 * H5支付 API client.
 *
 * <p>All outbound calls go through Spring HTTP Interface Client so the endpoint
 * contract stays close to the DTOs and service code does not need to build raw
 * WebClient requests.</p>
 */
@HttpExchange(contentType = MediaType.APPLICATION_JSON_VALUE, accept = MediaType.APPLICATION_JSON_VALUE)
public interface H5ZhiFuService {

    /**
     * PC 扫码支付。
     */
    @PostExchange("/native")
    Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> nativePay(@RequestBody H5ZhiFuPayDto dto);

    /**
     * 手机浏览器 H5 支付。
     */
    @PostExchange("/h5")
    Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> h5Pay(@RequestBody H5ZhiFuPayDto dto);

    /**
     * 微信内 JSAPI 支付。
     */
    @PostExchange("/jsapi")
    Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> jsapiPay(@RequestBody H5ZhiFuPayDto dto);
}
