package group.flyfish.dev.shop.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.bean.page.PageResult;
import group.flyfish.dev.shop.domain.dto.ShopCouponApplyDto;
import group.flyfish.dev.shop.domain.dto.ShopContractSignDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.vo.ShopCouponApplyVo;
import group.flyfish.dev.shop.domain.vo.ShopContractAgreementVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureProgressVo;
import group.flyfish.dev.shop.domain.vo.ShopItemDetailVo;
import group.flyfish.dev.shop.domain.vo.ShopItemGroupListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemListVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderCreateVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryDownloadVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderVo;
import group.flyfish.dev.shop.domain.vo.ShopPurchaseAvailabilityVo;
import group.flyfish.dev.shop.service.ShopService;
import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuNotifyDto;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.service.ShopContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商铺服务
 *
 * @author wangyu
 */
@RestController
@RequestMapping("shops")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ShopOrderService shopOrderService;
    private final ShopContractService shopContractService;

    /**
     * 获取当前店铺
     *
     * @return 结果
     */
    @GetMapping("current")
    public Mono<Result<Shop>> getCurrentShop() {
        return shopService.getCurrentShop().map(Result::ok);
    }

    /**
     * 获取商品分组
     *
     * @param shopId 商店id
     * @return 结果
     */
    @GetMapping("item-groups")
    public Mono<Result<List<ShopItemGroupListVo>>> getShopItemGroups(ShopItemGroupListQo qo) {
        return shopService.getItemGroupList(qo).collectList().map(Result::ok);
    }

    /**
     * 查询商铺商品列表
     *
     * @param qo 查询实体
     * @return 结果
     */
    @GetMapping("items")
    public Mono<Result<List<ShopItemListVo>>> getShopItems(ShopItemListQo qo) {
        return shopService.getItemList(qo).map(PageResult::ok);
    }

    /**
     * 查询商铺商品详情
     *
     * @param id 商品id
     * @return 结果
     */
    @GetMapping("items/{id}")
    public Mono<Result<ShopItemDetailVo>> getShopItemDetail(@PathVariable("id") Long id) {
        return shopService.getItemDetail(id).map(Result::ok);
    }

    /**
     * 查询商品购买前需要签署的合同。
     */
    @GetMapping("items/{id}/contracts")
    public Mono<Result<List<ShopContractAgreementVo>>> getShopItemContracts(@PathVariable("id") Long id) {
        return shopContractService.getItemAgreements(id).collectList().map(Result::ok);
    }

    /**
     * 记录当前用户对单个合同文件的阅读同意。
     */
    @PostMapping("items/{id}/contracts/signatures")
    public Mono<Result<ShopContractSignatureProgressVo>> agreeContractFile(@PathVariable("id") Long id,
                                                                           @Valid @RequestBody ShopContractSignDto dto,
                                                                           @CurrentUser PortalUserVo user,
                                                                           ServerWebExchange exchange) {
        return shopContractService.agreeFile(id, dto, user, exchange).map(Result::ok);
    }

    /**
     * 订单支付
     *
     * @param pay 支付信息
     * @return 结果
     */
    @PostMapping("payments")
    public Mono<Result<ShopOrderCreateVo>> pay(@RequestBody ShopOrderDto pay, @CurrentUser PortalUserVo user) {
        return shopOrderService.createOrder(pay, user).map(Result::ok);
    }

    /**
     * 创建订单并发起支付
     */
    @PostMapping("orders")
    public Mono<Result<ShopOrderCreateVo>> createOrder(@RequestBody ShopOrderDto order, @CurrentUser PortalUserVo user) {
        return shopOrderService.createOrder(order, user).map(Result::ok);
    }

    /**
     * 购买前检查，避免自动开通类商品重复购买。
     */
    @GetMapping("items/{id}/purchase-availability")
    public Mono<Result<ShopPurchaseAvailabilityVo>> checkPurchaseAvailability(@PathVariable("id") Long id,
                                                                              @CurrentUser PortalUserVo user) {
        return shopOrderService.checkPurchaseAvailability(id, user).map(Result::ok);
    }

    /**
     * 试算优惠券。
     */
    @PostMapping("coupons/apply")
    public Mono<Result<ShopCouponApplyVo>> applyCoupon(@Valid @RequestBody ShopCouponApplyDto dto,
                                                       @CurrentUser PortalUserVo user) {
        return shopOrderService.applyCoupon(dto, user).map(Result::ok);
    }

    /**
     * 查询当前用户订单
     */
    @GetMapping("orders")
    public Mono<Result<List<ShopOrderVo>>> getOrders(@CurrentUser PortalUserVo user, Long itemId) {
        return shopOrderService.getOrders(user, itemId).collectList().map(Result::ok);
    }

    /**
     * 查询当前用户自己的订单。即使当前用户具备管理权限，也只返回本人名下订单。
     */
    @GetMapping("orders/mine")
    public Mono<Result<List<ShopOrderVo>>> getMyOrders(@CurrentUser PortalUserVo user) {
        return shopOrderService.getMyOrders(user).collectList().map(Result::ok);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("orders/{orderNo}")
    public Mono<Result<ShopOrderVo>> getOrder(@PathVariable String orderNo, @CurrentUser PortalUserVo user) {
        return shopOrderService.getOrder(user, orderNo).map(Result::ok);
    }

    /**
     * 提取数字商品或授权许可的交付内容。
     */
    @PostMapping("orders/{orderNo}/delivery/extract")
    public Mono<Result<ShopOrderDeliveryExtractVo>> extractDelivery(@PathVariable String orderNo,
                                                                    @CurrentUser PortalUserVo user) {
        return shopOrderService.extractDelivery(user, orderNo).map(Result::ok);
    }

    /**
     * 下载订单交付文件。
     * <p>授权文件只通过该受登录态保护的接口下载，避免在普通 JSON 响应中暴露完整授权正文。</p>
     */
    @GetMapping("orders/{orderNo}/delivery/files/{fileCode}")
    public Mono<ResponseEntity<byte[]>> downloadDeliveryFile(@PathVariable String orderNo,
                                                             @PathVariable String fileCode,
                                                             @CurrentUser PortalUserVo user) {
        return shopOrderService.downloadDeliveryFile(user, orderNo, fileCode)
                .map(this::deliveryFileResponse);
    }

    /**
     * H5支付回调
     */
    @PostMapping(value = "payments/h5zhifu/notify", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> h5ZhiFuNotify(@RequestBody Map<String, Object> body) {
        return shopOrderService.handlePaymentNotify(H5ZhiFuNotifyDto.from(body)).thenReturn("success");
    }

    @PostMapping(value = "payments/h5zhifu/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> h5ZhiFuNotifyForm(ServerWebExchange exchange) {
        return exchange.getFormData()
                .map(this::toPlainParamMap)
                .flatMap(body -> shopOrderService.handlePaymentNotify(H5ZhiFuNotifyDto.from(body)))
                .thenReturn("success");
    }

    private Map<String, Object> toPlainParamMap(MultiValueMap<String, String> form) {
        Map<String, Object> body = new LinkedHashMap<>();
        form.forEach((key, value) -> body.put(key, value.isEmpty() ? null : value.get(0)));
        return body;
    }

    private ResponseEntity<byte[]> deliveryFileResponse(ShopOrderDeliveryDownloadVo file) {
        byte[] content = file.getContent() == null ? new byte[0] : file.getContent();
        MediaType mediaType = StringUtils.isBlank(file.getContentType())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content);
    }

}
