package group.flyfish.dev.shop.service.support.h5zhifu;

import group.flyfish.dev.common.exception.Assert;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.service.PayService;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * H5支付官方接口适配。
 *
 * <p>当前仅保留真实支付链路，不再提供本地模拟支付分支。这样可以避免测试数据绕过签名、
 * 回调幂等、订单流水和交付逻辑，保证开发、验证、生产看到的是同一套行为。</p>
 *
 * <p>官方文档约定三个支付场景分别调用不同路径：</p>
 * <ul>
 *     <li>PC 扫码支付：POST /api/native，返回 code_url。</li>
 *     <li>手机 H5 支付：POST /api/h5，返回 jump_url。</li>
 *     <li>微信 JSAPI 支付：POST /api/jsapi，pay_type 固定为 wechat，返回 jump_url。</li>
 * </ul>
 *
 * @author wangyu
 */
@RequiredArgsConstructor
public class H5ZhiFuPayService implements PayService {

    private static final String PROVIDER = "h5zhifu";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final H5ZhiFuService h5ZhiFuService;
    private final H5ZhiFuProperties properties;

    @Override
    public Mono<ShopOrderPaymentVo> pay(ShopOrder order, ShopItem item, ShopOrderDto request) {
        PayRequestContext context = resolveRequest(request);
        validateConfig();
        H5ZhiFuPayDto dto = buildPayDto(order, item, context);
        dto.setSign(H5ZhiFuSigner.sign(dto, properties.getKey()));
        return context.tradeType().submit(h5ZhiFuService, dto)
                .onErrorMap(WebClientResponseException.class, this::toProviderHttpError)
                .map(result -> {
                    Assert.notNull(result, "H5支付接口响应为空");
                    Assert.isTrue(result.getCode() != null
                                    && result.getCode() == H5ZhiFuResultDto.ResultCode.SUCCESS,
                            formatProviderError(result));
                    Assert.notNull(result.getData(), "H5支付接口未返回支付数据");
                    return convert(result.getData(), context);
                });
    }

    private H5ZhiFuPayDto buildPayDto(ShopOrder order, ShopItem item, PayRequestContext context) {
        H5ZhiFuPayDto dto = new H5ZhiFuPayDto();
        dto.setAppId(properties.getAppId());
        dto.setOutTradeNo(order.getOrderNo());
        dto.setDescription(StringUtils.abbreviate(item.getName(), 32));
        dto.setPayType(context.payType());
        dto.setAmount(toCent(order.getAmount()));
        dto.setAttach(order.getOrderNo());
        dto.setNotifyUrl(properties.getNotifyUrl());
        return dto;
    }

    private ShopOrderPaymentVo convert(H5ZhiFuPayResultDto result, PayRequestContext context) {
        if (context.tradeType() == TradeType.NATIVE) {
            Assert.isTrue(StringUtils.isNotBlank(result.getCodeUrl()), "H5支付扫码接口未返回二维码内容");
        } else {
            Assert.isTrue(StringUtils.isNotBlank(result.getJumpUrl()), "H5支付跳转接口未返回支付链接");
        }
        ShopOrderPaymentVo vo = new ShopOrderPaymentVo();
        vo.setProvider(PROVIDER);
        vo.setTradeType(context.tradeType().value);
        vo.setPayType(context.payType().name());
        vo.setTradeNo(result.getTradeNo());
        vo.setQrcodeText(result.getCodeUrl());
        vo.setJumpUrl(result.getJumpUrl());
        if (StringUtils.isNotBlank(result.getExpireTime())) {
            vo.setExpireTime(LocalDateTime.parse(result.getExpireTime(), TIME_FORMATTER));
        }
        return vo;
    }

    private String formatProviderError(H5ZhiFuResultDto<?> result) {
        String message = StringUtils.defaultIfBlank(result.getMsg(), "未知错误");
        return "H5支付发起失败：" + message + "（code=" + result.getCode() + "）";
    }

    private PayRequestContext resolveRequest(ShopOrderDto request) {
        String rawTradeType = normalize(request == null ? null : request.getTradeType());
        String rawPayType = normalize(request == null ? null : request.getPayType());
        if (StringUtils.isBlank(rawTradeType) && TradeType.supports(rawPayType)) {
            rawTradeType = rawPayType;
        }
        TradeType tradeType = TradeType.from(StringUtils.defaultIfBlank(rawTradeType, properties.getDefaultTradeType()));
        H5ZhiFuPayDto.PayType payType = resolvePayType(rawPayType, tradeType);
        return new PayRequestContext(tradeType, payType);
    }

    private H5ZhiFuPayDto.PayType resolvePayType(String rawPayType, TradeType tradeType) {
        if (tradeType == TradeType.JSAPI) {
            // 官方 JSAPI 接口的 pay_type 固定为 wechat，前端传 alipay 时也必须在这里纠偏。
            return H5ZhiFuPayDto.PayType.wechat;
        }
        String value = rawPayType;
        if (StringUtils.isBlank(value) || TradeType.supports(value) || "h5zhifu".equals(value)) {
            value = normalize(properties.getDefaultPayType());
        }
        try {
            return H5ZhiFuPayDto.PayType.valueOf(value);
        } catch (Exception e) {
            throw new ServiceException("支付渠道仅支持 wechat/alipay");
        }
    }

    private String normalize(String value) {
        return StringUtils.trimToEmpty(value).replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private Integer toCent(BigDecimal amount) {
        // 官方接口金额单位是“分”且必须为整数，数据库仍用元保存，出站时统一转换。
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private void validateConfig() {
        if (properties.getAppId() == null || StringUtils.isBlank(properties.getKey())
                || StringUtils.isBlank(properties.getNotifyUrl())) {
            throw new ServiceException("H5支付配置不完整，请配置 shop.payment.h5zhifu.app-id/key/notify-url");
        }
    }

    private record PayRequestContext(TradeType tradeType, H5ZhiFuPayDto.PayType payType) {
    }

    private enum TradeType {

        NATIVE("native"),
        H5("h5"),
        JSAPI("jsapi");

        private final String value;

        TradeType(String value) {
            this.value = value;
        }

        private Mono<H5ZhiFuResultDto<H5ZhiFuPayResultDto>> submit(H5ZhiFuService service, H5ZhiFuPayDto dto) {
            return switch (this) {
                case NATIVE -> service.nativePay(dto);
                case H5 -> service.h5Pay(dto);
                case JSAPI -> service.jsapiPay(dto);
            };
        }

        private static boolean supports(String value) {
            return "native".equals(value) || "scan".equals(value) || "pc".equals(value) || "qrcode".equals(value)
                    || "h5".equals(value) || "mobile".equals(value)
                    || "jsapi".equals(value) || "wechat_jsapi".equals(value);
        }

        private static TradeType from(String value) {
            String normalized = StringUtils.trimToEmpty(value).replace('-', '_').toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "native", "scan", "pc", "qrcode" -> NATIVE;
                case "h5", "mobile" -> H5;
                case "jsapi", "wechat_jsapi" -> JSAPI;
                default -> throw new ServiceException("支付场景仅支持 native/h5/jsapi");
            };
        }
    }

    private ServiceException toProviderHttpError(WebClientResponseException e) {
        String body = StringUtils.defaultIfBlank(e.getResponseBodyAsString(), e.getStatusText());
        return new ServiceException("H5支付接口HTTP调用失败：" + e.getStatusCode().value() + " " + body);
    }
}
