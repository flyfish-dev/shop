package group.flyfish.dev.shop.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.git.service.GitRepositoryLookupService;
import group.flyfish.dev.shop.domain.dto.*;
import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.vo.ShopContractFileVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureRecordVo;
import group.flyfish.dev.shop.domain.vo.ShopContractVo;
import group.flyfish.dev.shop.domain.vo.ShopCouponVo;
import group.flyfish.dev.shop.domain.vo.ShopManageWorkbenchSummaryVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderVo;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopManageWorkbenchService;
import group.flyfish.dev.shop.service.ShopManageService;
import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
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
    private final ShopManageWorkbenchService shopManageWorkbenchService;
    private final ShopOrderService shopOrderService;
    private final ShopCouponService shopCouponService;
    private final ShopContractService shopContractService;
    private final AuthUserGateway authUserGateway;
    private final GitRepositoryLookupService gitRepositoryLookupService;
    private final UploadService uploadService;

    /**
     * 查询小铺管理工作台统计摘要。
     */
    @GetMapping("workbench/summary")
    public Mono<Result<ShopManageWorkbenchSummaryVo>> workbenchSummary(@CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageWorkbenchService.summary().map(Result::ok);
    }

    /**
     * 查询小铺管理工作台首屏核心指标。
     */
    @GetMapping("workbench/overview")
    public Mono<Result<ShopManageWorkbenchSummaryVo>> workbenchOverview(@CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageWorkbenchService.overview().map(Result::ok);
    }

    /**
     * 查询小铺管理工作台运营洞察指标。
     */
    @GetMapping("workbench/insights")
    public Mono<Result<ShopManageWorkbenchSummaryVo>> workbenchInsights(@CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopManageWorkbenchService.insights().map(Result::ok);
    }

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
     * 查询后台合同列表。
     */
    @GetMapping("contracts")
    public Mono<Result<List<ShopContractVo>>> contracts(@CurrentUser PortalUserVo user,
                                                        @RequestParam(defaultValue = "false") boolean enabledOnly) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return (enabledOnly ? shopContractService.listEnabledContracts() : shopContractService.listContracts())
                .collectList()
                .map(Result::ok);
    }

    /**
     * 创建合同。
     */
    @PostMapping("contracts")
    public Mono<Result<ShopContractVo>> createContract(@Valid @RequestBody ShopContractCreateDto dto,
                                                       @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.createContract(dto).map(Result::ok);
    }

    /**
     * 更新合同。
     */
    @PutMapping("contracts/{id}")
    public Mono<Result<ShopContractVo>> updateContract(@NotNull @PathVariable Long id,
                                                       @Valid @RequestBody ShopContractUpdateDto dto,
                                                       @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.updateContract(id, dto).map(Result::ok);
    }

    /**
     * 删除合同。
     */
    @DeleteMapping("contracts/{id}")
    public Mono<Result<Void>> deleteContract(@NotNull @PathVariable Long id, @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.deleteContract(id).thenReturn(Result.ok());
    }

    /**
     * 上传合同文件。文件类型不限制，预览由前端 file-viewer 统一承担。
     */
    @PostMapping(value = "contracts/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<ShopContractFileVo>> uploadContractFile(@NotNull @PathVariable Long id,
                                                               @RequestPart("file") FilePart file,
                                                               @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.uploadContractFile(id, file).map(Result::ok);
    }

    /**
     * 更新合同文件展示信息。
     */
    @PutMapping("contracts/{id}/files/{fileId}")
    public Mono<Result<ShopContractFileVo>> updateContractFile(@NotNull @PathVariable Long id,
                                                               @NotNull @PathVariable Long fileId,
                                                               @Valid @RequestBody ShopContractFileUpdateDto dto,
                                                               @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.updateContractFile(id, fileId, dto).map(Result::ok);
    }

    /**
     * 删除合同文件。
     */
    @DeleteMapping("contracts/{id}/files/{fileId}")
    public Mono<Result<Void>> deleteContractFile(@NotNull @PathVariable Long id,
                                                 @NotNull @PathVariable Long fileId,
                                                 @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.deleteContractFile(id, fileId).thenReturn(Result.ok());
    }

    /**
     * 查询合同签署留痕。
     */
    @GetMapping("contract-signatures")
    public Mono<Result<List<ShopContractSignatureRecordVo>>> contractSignatures(@CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopContractService.listSignatureRecords().collectList().map(Result::ok);
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
        return authUserGateway.listUsers(keyword).collectList().map(Result::ok);
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
     * 查看订单交付快照。
     * <p>管理员核查交付内容时只读查看，不会改变用户首次提取时间。</p>
     */
    @GetMapping("orders/{orderNo}/delivery")
    public Mono<Result<ShopOrderDeliveryExtractVo>> viewOrderDelivery(@NotNull @PathVariable String orderNo,
                                                                      @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopOrderService.viewDelivery(user, orderNo).map(Result::ok);
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

    /**
     * 重试自动交付。
     * <p>只允许管理员对已支付且自动交付失败的订单发起，具体交付动作仍由商品交付策略幂等处理。</p>
     */
    @PostMapping("orders/{orderNo}/delivery/retry")
    public Mono<Result<ShopOrderVo>> retryOrderDelivery(@NotNull @PathVariable String orderNo,
                                                        @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return shopOrderService.retryDelivery(orderNo).map(Result::ok);
    }
}
