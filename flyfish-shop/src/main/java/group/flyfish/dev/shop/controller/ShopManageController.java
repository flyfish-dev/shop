package group.flyfish.dev.shop.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.git.service.GitRepositoryLookupService;
import group.flyfish.dev.shop.domain.dto.*;
import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.vo.ShopCouponVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderVo;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopManageService;
import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import group.flyfish.dev.user.service.PortalUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 门店管理服务
 *
 * @author wangyu
 */
@Validated
@RestController
@RequestMapping("/shops/managements")
@RequiredArgsConstructor
public class ShopManageController {

    private final ShopManageService shopManageService;
    private final ShopOrderService shopOrderService;
    private final ShopCouponService shopCouponService;
    private final PortalUserService portalUserService;
    private final GitRepositoryLookupService gitRepositoryLookupService;
    private final UploadService uploadService;

    /**
     * 上传商品图片
     */
    @PostMapping(value = "upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<String>> uploadImage(@RequestPart("file") FilePart file, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return uploadService.upload(file)
                .map(metadata -> Result.accept(metadata.getUrl()));
    }

    /**
     * 创建门店
     */
    @PostMapping
    public Mono<Result<Shop>> createShop(@Valid @RequestBody ShopCreateDto dto, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.createShop(dto).map(Result::ok);
    }

    /**
     * 更新门店信息
     */
    @PutMapping("{id}")
    public Mono<Result<Shop>> updateShop(@NotNull @PathVariable Long id, @Valid @RequestBody ShopUpdateDto dto,
                                         @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.updateShop(id, dto).map(Result::ok);
    }

    /**
     * 删除门店
     */
    @DeleteMapping("{id}")
    public Mono<Result<Void>> deleteShop(@NotNull @PathVariable Long id, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.deleteShop(id).thenReturn(Result.ok());
    }

    /**
     * 创建商品分组
     */
    @PostMapping("item-groups")
    public Mono<Result<Void>> createItemGroup(@Valid @RequestBody ShopItemGroupCreateDto dto, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.createItemGroup(dto).thenReturn(Result.ok());
    }

    /**
     * 更新商品分组
     */
    @PutMapping("item-groups/{id}")
    public Mono<Result<Void>> updateItemGroup(@NotNull @PathVariable Long id, @Valid @RequestBody ShopItemGroupUpdateDto dto,
                                              @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.updateItemGroup(id, dto).thenReturn(Result.ok());
    }

    /**
     * 删除商品分组
     */
    @DeleteMapping("item-groups/{id}")
    public Mono<Result<Void>> deleteItemGroup(@NotNull @PathVariable Long id, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.deleteItemGroup(id).thenReturn(Result.ok());
    }

    /**
     * 创建商品
     */
    @PostMapping("items")
    public Mono<Result<Void>> createItem(@Valid @RequestBody ShopItemCreateDto dto, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.createItem(dto).thenReturn(Result.ok());
    }

    /**
     * 更新商品信息
     */
    @PutMapping("items/{id}")
    public Mono<Result<Void>> updateItem(@NotNull @PathVariable Long id, @Valid @RequestBody ShopItemUpdateDto dto,
                                         @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.updateItem(id, dto).thenReturn(Result.ok());
    }

    /**
     * 删除商品
     */
    @DeleteMapping("items/{id}")
    public Mono<Result<Void>> deleteItem(@NotNull @PathVariable Long id, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageService.deleteItem(id).thenReturn(Result.ok());
    }

    /**
     * 根据管理令牌列出可维护的 Git 仓库，商品表单直接选择后保存标准 owner/repo。
     */
    @GetMapping("repositories")
    public Mono<Result<List<GitRepositoryOptionVo>>> repositories(@RequestParam(defaultValue = "gitea") String provider,
                                                                  @RequestParam(required = false) String q,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "50") Integer size,
                                                                  @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return gitRepositoryLookupService.list(provider, q, page, size)
                .collectList()
                .map(Result::ok);
    }

    /**
     * 查询门户用户，便于管理员在订单、客服和售后场景中核实客户身份。
     */
    @GetMapping("users")
    public Mono<Result<List<PortalUserVo>>> users(@RequestParam(required = false) String keyword,
                                                  @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return portalUserService.listUsers(keyword).collectList().map(Result::ok);
    }

    /**
     * 查询后台优惠券。
     */
    @GetMapping("coupons")
    public Mono<Result<List<ShopCouponVo>>> coupons(@CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopCouponService.listCoupons().collectList().map(Result::ok);
    }

    /**
     * 生成优惠券。
     */
    @PostMapping("coupons")
    public Mono<Result<ShopCouponVo>> createCoupon(@Valid @RequestBody ShopCouponCreateDto dto,
                                                   @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopCouponService.createCoupon(dto).map(Result::ok);
    }

    /**
     * 更新优惠券。
     */
    @PutMapping("coupons/{id}")
    public Mono<Result<ShopCouponVo>> updateCoupon(@NotNull @PathVariable Long id,
                                                   @Valid @RequestBody ShopCouponUpdateDto dto,
                                                   @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopCouponService.updateCoupon(id, dto).map(Result::ok);
    }

    /**
     * 删除优惠券。
     */
    @DeleteMapping("coupons/{id}")
    public Mono<Result<Void>> deleteCoupon(@NotNull @PathVariable Long id,
                                           @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopCouponService.deleteCoupon(id).thenReturn(Result.ok());
    }

    /**
     * 处理订单交付结果
     */
    @PutMapping("orders/{orderNo}/delivery")
    public Mono<Result<ShopOrderVo>> updateOrderDelivery(@NotNull @PathVariable String orderNo,
                                                        @Valid @RequestBody ShopOrderDeliveryDto dto,
                                                        @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopOrderService.updateDelivery(orderNo, dto).map(Result::ok);
    }
}
