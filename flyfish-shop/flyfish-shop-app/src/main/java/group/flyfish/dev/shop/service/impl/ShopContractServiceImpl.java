package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.upload.domain.FileMetadata;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.shop.domain.dto.ShopContractCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopContractFileUpdateDto;
import group.flyfish.dev.shop.domain.dto.ShopContractSignDto;
import group.flyfish.dev.shop.domain.dto.ShopContractUpdateDto;
import group.flyfish.dev.shop.domain.po.ShopContract;
import group.flyfish.dev.shop.domain.po.ShopContractFile;
import group.flyfish.dev.shop.domain.po.ShopContractSignature;
import group.flyfish.dev.shop.domain.po.ShopItemContract;
import group.flyfish.dev.shop.domain.vo.ShopContractAgreementVo;
import group.flyfish.dev.shop.domain.vo.ShopContractFileVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureProgressVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureRecordVo;
import group.flyfish.dev.shop.domain.vo.ShopContractVo;
import group.flyfish.dev.shop.repository.ShopContractFileRepository;
import group.flyfish.dev.shop.repository.ShopContractRepository;
import group.flyfish.dev.shop.repository.ShopContractSignatureRepository;
import group.flyfish.dev.shop.repository.ShopItemContractRepository;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 小铺合同服务实现。
 */
@Service
@RequiredArgsConstructor
public class ShopContractServiceImpl implements ShopContractService {

    private final ShopContractRepository shopContractRepository;
    private final ShopContractFileRepository shopContractFileRepository;
    private final ShopItemContractRepository shopItemContractRepository;
    private final ShopContractSignatureRepository shopContractSignatureRepository;
    private final UploadService uploadService;

    @Override
    public Flux<ShopContractVo> listContracts() {
        return shopContractRepository.findAllOrderBySort().concatMap(this::toVoAsync);
    }

    @Override
    public Flux<ShopContractVo> listEnabledContracts() {
        return shopContractRepository.findAllOrderBySort()
                .filter(contract -> Boolean.TRUE.equals(contract.getEnabled()))
                .concatMap(this::toVoAsync);
    }

    @Override
    @Transactional
    public Mono<ShopContractVo> createContract(ShopContractCreateDto dto) {
        ShopContract contract = new ShopContract();
        contract.setName(StringUtils.trim(dto.getName()));
        contract.setType(dto.getType());
        contract.setDescription(StringUtils.trimToNull(dto.getDescription()));
        contract.setTags(joinTags(dto.getTags()));
        contract.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        contract.setSort(dto.getSort() == null ? 0 : dto.getSort());
        return shopContractRepository.save(contract).flatMap(this::toVoAsync);
    }

    @Override
    @Transactional
    public Mono<ShopContractVo> updateContract(Long id, ShopContractUpdateDto dto) {
        return shopContractRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("CONTRACT_NOT_FOUND", "合同不存在")))
                .map(contract -> {
                    if (dto.getName() != null) {
                        contract.setName(StringUtils.trim(dto.getName()));
                    }
                    if (dto.getType() != null) {
                        contract.setType(dto.getType());
                    }
                    if (dto.getDescription() != null) {
                        contract.setDescription(StringUtils.trimToNull(dto.getDescription()));
                    }
                    if (dto.getTags() != null) {
                        contract.setTags(joinTags(dto.getTags()));
                    }
                    if (dto.getEnabled() != null) {
                        contract.setEnabled(dto.getEnabled());
                    }
                    if (dto.getSort() != null) {
                        contract.setSort(dto.getSort());
                    }
                    return contract;
                })
                .flatMap(shopContractRepository::save)
                .flatMap(this::toVoAsync);
    }

    @Override
    @Transactional
    public Mono<Void> deleteContract(Long id) {
        return shopContractRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("CONTRACT_NOT_FOUND", "合同不存在")))
                .flatMap(contract -> shopContractRepository.delete(contract));
    }

    @Override
    @Transactional
    public Mono<ShopContractFileVo> uploadContractFile(Long contractId, FilePart file) {
        return requireContract(contractId)
                .then(uploadService.upload(file))
                .map(metadata -> toFile(contractId, metadata))
                .flatMap(shopContractFileRepository::save)
                .map(this::toFileVo);
    }

    @Override
    @Transactional
    public Mono<ShopContractFileVo> updateContractFile(Long contractId, Long fileId, ShopContractFileUpdateDto dto) {
        return findContractFile(contractId, fileId)
                .map(file -> {
                    if (StringUtils.isNotBlank(dto.getFileName())) {
                        file.setFileName(StringUtils.trim(dto.getFileName()));
                    }
                    if (dto.getEnabled() != null) {
                        file.setEnabled(dto.getEnabled());
                    }
                    if (dto.getSort() != null) {
                        file.setSort(dto.getSort());
                    }
                    return file;
                })
                .flatMap(shopContractFileRepository::save)
                .map(this::toFileVo);
    }

    @Override
    @Transactional
    public Mono<Void> deleteContractFile(Long contractId, Long fileId) {
        return findContractFile(contractId, fileId).flatMap(shopContractFileRepository::delete);
    }

    @Override
    @Transactional
    public Mono<Void> updateItemContracts(Long itemId, List<Long> contractIds) {
        List<Long> normalized = normalizeIds(contractIds);
        return shopItemContractRepository.deleteByItemId(itemId)
                .thenMany(Flux.fromIterable(normalized).index())
                .map(tuple -> {
                    ShopItemContract binding = new ShopItemContract();
                    binding.setItemId(itemId);
                    binding.setContractId(tuple.getT2());
                    binding.setRequired(true);
                    binding.setEnabled(true);
                    binding.setSort(tuple.getT1().intValue());
                    return binding;
                })
                .flatMap(shopItemContractRepository::save)
                .then();
    }

    @Override
    public Mono<List<Long>> getItemContractIds(Long itemId) {
        return shopItemContractRepository.findEnabledByItemId(itemId)
                .map(ShopItemContract::getContractId)
                .collectList();
    }

    @Override
    public Mono<Boolean> hasActiveContracts(Long itemId) {
        return getItemAgreementFiles(itemId)
                .hasElements();
    }

    @Override
    public Flux<ShopContractAgreementVo> getItemAgreements(Long itemId) {
        return getItemContractIds(itemId)
                .flatMapMany(contractIds -> shopContractRepository.findEnabledByIds(contractIds).collectList()
                        .flatMapMany(contracts -> Flux.fromIterable(contracts)
                                .concatMap(contract -> shopContractFileRepository.findEnabledByContractId(contract.getId())
                                        .map(this::toFileVo)
                                        .collectList()
                                        .filter(files -> !files.isEmpty())
                                        .map(files -> toAgreementVo(contract, files)))));
    }

    @Override
    @Transactional
    public Mono<ShopContractSignatureProgressVo> agreeFile(Long itemId, ShopContractSignDto dto,
                                                           PortalUserVo buyer, ServerWebExchange exchange) {
        ShopAuthorizationUtils.requireLogin(buyer);
        String signToken = StringUtils.defaultIfBlank(dto.getSignToken(), IdGenerators.uuid32());
        return requireAgreementFile(itemId, dto.getContractId(), dto.getFileId())
                .flatMap(pair -> shopContractSignatureRepository
                        .findSignedFile(signToken, buyer.getId(), itemId, dto.getFileId())
                        .switchIfEmpty(Mono.defer(() -> createSignature(signToken, itemId, buyer, pair.contract(),
                                pair.file(), dto.getReadPercent(), exchange)))
                        .flatMap(signature -> {
                            signature.setReadPercent(normalizeReadPercent(dto.getReadPercent()));
                            signature.setStatus(ShopContractSignature.Status.AGREED);
                            signature.setAgreedTime(LocalDateTime.now());
                            signature.setClientIp(clientIp(exchange));
                            signature.setUserAgent(userAgent(exchange));
                            return shopContractSignatureRepository.save(signature);
                        }))
                .then(progress(itemId, buyer.getId(), signToken));
    }

    @Override
    public Mono<Void> requireSigned(Long itemId, PortalUserVo buyer, String signToken) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return getItemAgreementFiles(itemId).collectList().flatMap(files -> {
            if (files.isEmpty()) {
                return Mono.empty();
            }
            if (StringUtils.isBlank(signToken)) {
                return Mono.error(new BusinessException("CONTRACT_SIGNATURE_REQUIRED", "请先阅读并同意合同后再付款"));
            }
            Set<Long> requiredFileIds = files.stream()
                    .map(AgreementFile::file)
                    .map(ShopContractFile::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return shopContractSignatureRepository.findAgreedByToken(signToken, buyer.getId(), itemId)
                    .map(ShopContractSignature::getContractFileId)
                    .filter(requiredFileIds::contains)
                    .distinct()
                    .count()
                    .flatMap(agreedCount -> agreedCount >= requiredFileIds.size()
                            ? Mono.empty()
                            : Mono.error(new BusinessException("CONTRACT_SIGNATURE_REQUIRED", "请先阅读并同意全部合同后再付款")));
        });
    }

    @Override
    @Transactional
    public Mono<Void> bindOrder(String signToken, String orderNo, Long itemId, Long buyerId) {
        if (StringUtils.isAnyBlank(signToken, orderNo) || itemId == null || buyerId == null) {
            return Mono.empty();
        }
        return shopContractSignatureRepository.bindOrder(signToken, buyerId, itemId, orderNo).then();
    }

    @Override
    public Flux<ShopContractSignatureRecordVo> listSignatureRecords() {
        return shopContractSignatureRepository.findAllOrderByCreateTimeDesc().map(this::toRecordVo);
    }

    private Mono<ShopContract> requireContract(Long contractId) {
        return shopContractRepository.findById(contractId)
                .switchIfEmpty(Mono.error(new BusinessException("CONTRACT_NOT_FOUND", "合同不存在")));
    }

    private Mono<ShopContractFile> findContractFile(Long contractId, Long fileId) {
        return shopContractFileRepository.findById(fileId)
                .filter(file -> Objects.equals(contractId, file.getContractId()))
                .switchIfEmpty(Mono.error(new BusinessException("CONTRACT_FILE_NOT_FOUND", "合同文件不存在")));
    }

    private Mono<AgreementFile> requireAgreementFile(Long itemId, Long contractId, Long fileId) {
        return getItemAgreementFiles(itemId)
                .filter(file -> Objects.equals(contractId, file.contract().getId())
                        && Objects.equals(fileId, file.file().getId()))
                .next()
                .switchIfEmpty(Mono.error(new BusinessException("CONTRACT_FILE_NOT_FOUND", "合同文件不存在或未绑定到商品")));
    }

    private Flux<AgreementFile> getItemAgreementFiles(Long itemId) {
        return getItemContractIds(itemId)
                .flatMapMany(contractIds -> {
                    if (contractIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return shopContractRepository.findEnabledByIds(contractIds)
                            .flatMap(contract -> shopContractFileRepository.findEnabledByContractId(contract.getId())
                                    .map(file -> new AgreementFile(contract, file)));
                });
    }

    private Mono<ShopContractSignature> createSignature(String signToken, Long itemId, PortalUserVo buyer,
                                                        ShopContract contract, ShopContractFile file,
                                                        Integer readPercent, ServerWebExchange exchange) {
        ShopContractSignature signature = new ShopContractSignature();
        signature.setSignToken(signToken);
        signature.setItemId(itemId);
        signature.setBuyerId(buyer.getId());
        signature.setContractId(contract.getId());
        signature.setContractFileId(file.getId());
        signature.setContractName(contract.getName());
        signature.setContractType(contract.getType() == null ? null : contract.getType().name());
        signature.setFileName(file.getFileName());
        signature.setFileUrl(file.getFileUrl());
        signature.setReadPercent(normalizeReadPercent(readPercent));
        signature.setStatus(ShopContractSignature.Status.AGREED);
        signature.setAgreedTime(LocalDateTime.now());
        signature.setClientIp(clientIp(exchange));
        signature.setUserAgent(userAgent(exchange));
        return Mono.just(signature);
    }

    private Mono<ShopContractSignatureProgressVo> progress(Long itemId, Long buyerId, String signToken) {
        return getItemAgreementFiles(itemId).collectList()
                .zipWith(shopContractSignatureRepository.findAgreedByToken(signToken, buyerId, itemId)
                        .map(ShopContractSignature::getContractFileId)
                        .distinct()
                        .collectList())
                .map(tuple -> {
                    Set<Long> requiredIds = tuple.getT1().stream()
                            .map(AgreementFile::file)
                            .map(ShopContractFile::getId)
                            .collect(Collectors.toSet());
                    long agreed = tuple.getT2().stream().filter(requiredIds::contains).count();
                    ShopContractSignatureProgressVo vo = new ShopContractSignatureProgressVo();
                    vo.setSignToken(signToken);
                    vo.setTotalCount(requiredIds.size());
                    vo.setAgreedCount((int) agreed);
                    vo.setCompleted(agreed >= requiredIds.size());
                    return vo;
                });
    }

    private ShopContractFile toFile(Long contractId, FileMetadata metadata) {
        ShopContractFile file = new ShopContractFile();
        file.setContractId(contractId);
        file.setFileName(metadata.getOriginalFilename());
        file.setFileUrl(metadata.getUrl());
        file.setContentType(metadata.getContentType());
        file.setFileSize(metadata.getSize());
        file.setSort(0);
        file.setEnabled(true);
        return file;
    }

    private Mono<ShopContractVo> toVoAsync(ShopContract contract) {
        return shopContractFileRepository.findAllByContractId(contract.getId())
                .map(this::toFileVo)
                .collectList()
                .map(files -> {
                    ShopContractVo vo = new ShopContractVo();
                    vo.setId(contract.getId());
                    vo.setName(contract.getName());
                    vo.setType(contract.getType() == null ? null : contract.getType().name());
                    vo.setTypeName(contract.getType() == null ? null : contract.getType().getTitle());
                    vo.setDescription(contract.getDescription());
                    vo.setTags(splitTags(contract.getTags()));
                    vo.setEnabled(contract.getEnabled());
                    vo.setSort(contract.getSort());
                    vo.setFiles(files);
                    vo.setCreateTime(contract.getCreateTime());
                    vo.setUpdateTime(contract.getUpdateTime());
                    return vo;
                });
    }

    private ShopContractAgreementVo toAgreementVo(ShopContract contract, List<ShopContractFileVo> files) {
        ShopContractAgreementVo vo = new ShopContractAgreementVo();
        vo.setId(contract.getId());
        vo.setName(contract.getName());
        vo.setType(contract.getType() == null ? null : contract.getType().name());
        vo.setTypeName(contract.getType() == null ? null : contract.getType().getTitle());
        vo.setDescription(contract.getDescription());
        vo.setTags(splitTags(contract.getTags()));
        vo.setFiles(files);
        return vo;
    }

    private ShopContractFileVo toFileVo(ShopContractFile file) {
        ShopContractFileVo vo = new ShopContractFileVo();
        vo.setId(file.getId());
        vo.setContractId(file.getContractId());
        vo.setFileName(file.getFileName());
        vo.setFileUrl(file.getFileUrl());
        vo.setContentType(file.getContentType());
        vo.setFileSize(file.getFileSize());
        vo.setSort(file.getSort());
        vo.setEnabled(file.getEnabled());
        return vo;
    }

    private ShopContractSignatureRecordVo toRecordVo(ShopContractSignature signature) {
        ShopContractSignatureRecordVo vo = new ShopContractSignatureRecordVo();
        vo.setId(signature.getId());
        vo.setSignToken(signature.getSignToken());
        vo.setOrderNo(signature.getOrderNo());
        vo.setItemId(signature.getItemId());
        vo.setBuyerId(signature.getBuyerId());
        vo.setContractId(signature.getContractId());
        vo.setContractFileId(signature.getContractFileId());
        vo.setContractName(signature.getContractName());
        vo.setContractType(signature.getContractType());
        vo.setFileName(signature.getFileName());
        vo.setFileUrl(signature.getFileUrl());
        vo.setReadPercent(signature.getReadPercent());
        vo.setStatus(signature.getStatus() == null ? null : signature.getStatus().name());
        vo.setAgreedTime(signature.getAgreedTime());
        vo.setClientIp(signature.getClientIp());
        vo.setUserAgent(signature.getUserAgent());
        vo.setCreateTime(signature.getCreateTime());
        return vo;
    }

    private String joinTags(Collection<String> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> splitTags(String tags) {
        if (StringUtils.isBlank(tags)) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Integer normalizeReadPercent(Integer readPercent) {
        if (readPercent == null) {
            return 100;
        }
        return Math.max(0, Math.min(100, readPercent));
    }

    private String clientIp(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String forwardedFor = headers.getFirst("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.substringBefore(forwardedFor, ",").trim();
        }
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    private String userAgent(ServerWebExchange exchange) {
        return StringUtils.abbreviate(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT), 512);
    }

    private record AgreementFile(ShopContract contract, ShopContractFile file) {
    }
}
