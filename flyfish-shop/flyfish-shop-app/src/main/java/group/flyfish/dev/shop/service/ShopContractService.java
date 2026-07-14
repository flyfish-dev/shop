package group.flyfish.dev.shop.service;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.domain.dto.ShopContractCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopContractFileUpdateDto;
import group.flyfish.dev.shop.domain.dto.ShopContractSignDto;
import group.flyfish.dev.shop.domain.dto.ShopContractUpdateDto;
import group.flyfish.dev.shop.domain.vo.ShopContractAgreementVo;
import group.flyfish.dev.shop.domain.vo.ShopContractFileVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureProgressVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureRecordVo;
import group.flyfish.dev.shop.domain.vo.ShopContractVo;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 小铺合同服务。
 */
public interface ShopContractService {

    Flux<ShopContractVo> listContracts();

    Flux<ShopContractVo> listEnabledContracts();

    Mono<ShopContractVo> createContract(ShopContractCreateDto dto);

    Mono<ShopContractVo> updateContract(Long id, ShopContractUpdateDto dto);

    Mono<Void> deleteContract(Long id);

    Mono<ShopContractFileVo> uploadContractFile(Long contractId, FilePart file);

    Mono<ShopContractFileVo> updateContractFile(Long contractId, Long fileId, ShopContractFileUpdateDto dto);

    Mono<Void> deleteContractFile(Long contractId, Long fileId);

    Mono<Void> updateItemContracts(Long itemId, List<Long> contractIds);

    Mono<Void> updateSkuContracts(Long itemId, Long skuId, List<Long> contractIds);

    Mono<List<Long>> getItemContractIds(Long itemId);

    Mono<List<Long>> getSkuContractIds(Long skuId);

    Mono<Boolean> hasActiveContracts(Long itemId);

    Mono<Boolean> hasActiveContracts(Long itemId, Long skuId);

    Flux<ShopContractAgreementVo> getItemAgreements(Long itemId);

    Flux<ShopContractAgreementVo> getItemAgreements(Long itemId, Long skuId);

    Mono<ShopContractSignatureProgressVo> agreeFile(Long itemId, ShopContractSignDto dto,
                                                    PortalUserVo buyer, ServerWebExchange exchange);

    Mono<ShopContractSignatureProgressVo> agreeFile(Long itemId, Long skuId, ShopContractSignDto dto,
                                                    PortalUserVo buyer, ServerWebExchange exchange);

    Mono<Void> requireSigned(Long itemId, PortalUserVo buyer, String signToken);

    Mono<Void> requireSigned(Long itemId, Long skuId, PortalUserVo buyer, String signToken);

    Mono<Void> bindOrder(String signToken, String orderNo, Long itemId, Long buyerId);

    Mono<Void> bindOrder(String signToken, String orderNo, Long itemId, Long skuId, Long buyerId);

    Flux<ShopContractSignatureRecordVo> listSignatureRecords();
}
