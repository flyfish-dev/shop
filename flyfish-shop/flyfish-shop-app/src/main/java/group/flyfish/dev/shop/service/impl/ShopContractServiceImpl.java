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
import group.flyfish.dev.shop.domain.po.ShopItemSkuContract;
import group.flyfish.dev.shop.domain.vo.ShopContractAgreementVo;
import group.flyfish.dev.shop.domain.vo.ShopContractFileVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureProgressVo;
import group.flyfish.dev.shop.domain.vo.ShopContractSignatureRecordVo;
import group.flyfish.dev.shop.domain.vo.ShopContractVo;
import group.flyfish.dev.shop.repository.ShopContractFileRepository;
import group.flyfish.dev.shop.repository.ShopContractRepository;
import group.flyfish.dev.shop.repository.ShopContractSignatureRepository;
import group.flyfish.dev.shop.repository.ShopItemContractRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuContractRepository;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.support.ShopContractLocalization;
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
import java.util.Map;
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
    private final ShopItemSkuContractRepository shopItemSkuContractRepository;
    private final ShopContractSignatureRepository shopContractSignatureRepository;
    private final UploadService uploadService;
    private final ShopContractLocalization shopContractLocalization;

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
        contract.setI18n(shopContractLocalization.serialize(dto.getI18n()));
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
                    if (dto.getI18n() != null) {
                        contract.setI18n(shopContractLocalization.serialize(dto.getI18n()));
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
    public Mono<ShopContractFileVo> uploadContractFile(Long contractId, String locale, String documentKey,
                                                       FilePart file) {
        return requireContract(contractId)
                .then(uploadService.upload(file))
                .flatMap(metadata -> shopContractFileRepository.findAllByContractId(contractId).collectList()
                        .flatMap(existing -> {
                            String normalizedLocale = shopContractLocalization.normalizeLocale(locale);
                            String normalizedKey = shopContractLocalization.normalizeDocumentKey(
                                    documentKey, metadata.getOriginalFilename());
                            int version = shopContractLocalization.nextVersion(
                                    existing, normalizedKey, normalizedLocale);
                            ShopContractFile contractFile = toFile(
                                    contractId, normalizedLocale, normalizedKey, version, metadata);
                            return retirePreviousVersion(existing, normalizedKey, version)
                                    .then(shopContractFileRepository.save(contractFile));
                        }))
                .map(this::toFileVo);
    }

    @Override
    @Transactional
    public Mono<ShopContractFileVo> updateContractFile(Long contractId, Long fileId, ShopContractFileUpdateDto dto) {
        return findContractFile(contractId, fileId)
                .flatMap(file -> shopContractFileRepository.findAllByContractId(contractId).collectList()
                        .flatMap(existing -> {
                            String originalKey = shopContractLocalization.documentKey(file);
                            String originalLocale = shopContractLocalization.normalizeLocale(file.getLocale());
                            if (StringUtils.isNotBlank(dto.getFileName())) {
                                file.setFileName(StringUtils.trim(dto.getFileName()));
                            }
                            if (dto.getDocumentKey() != null) {
                                file.setDocumentKey(shopContractLocalization.normalizeDocumentKey(
                                        dto.getDocumentKey(), file.getFileName()));
                            }
                            if (dto.getLocale() != null) {
                                file.setLocale(shopContractLocalization.normalizeLocale(dto.getLocale()));
                            }
                            if (dto.getEnabled() != null) {
                                file.setEnabled(dto.getEnabled());
                            }
                            if (dto.getSort() != null) {
                                file.setSort(dto.getSort());
                            }
                            boolean moved = !Objects.equals(originalKey, shopContractLocalization.documentKey(file))
                                    || !Objects.equals(originalLocale,
                                    shopContractLocalization.normalizeLocale(file.getLocale()));
                            if (moved) {
                                List<ShopContractFile> others = existing.stream()
                                        .filter(candidate -> !Objects.equals(candidate.getId(), file.getId()))
                                        .toList();
                                file.setFileVersion(shopContractLocalization.nextVersion(
                                        others, shopContractLocalization.documentKey(file), file.getLocale()));
                            }
                            return retirePreviousVersion(existing, shopContractLocalization.documentKey(file),
                                    shopContractLocalization.fileVersion(file))
                                    .then(disableConflictingVariant(existing, file))
                                    .then(shopContractFileRepository.save(file));
                        }))
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
    @Transactional
    public Mono<Void> updateSkuContracts(Long itemId, Long skuId, List<Long> contractIds) {
        if (skuId == null) {
            return Mono.empty();
        }
        List<Long> normalized = normalizeIds(contractIds);
        return shopItemSkuContractRepository.deleteBySkuId(skuId)
                .thenMany(Flux.fromIterable(normalized).index())
                .map(tuple -> {
                    ShopItemSkuContract binding = new ShopItemSkuContract();
                    binding.setItemId(itemId);
                    binding.setSkuId(skuId);
                    binding.setContractId(tuple.getT2());
                    binding.setRequired(true);
                    binding.setEnabled(true);
                    binding.setSort(tuple.getT1().intValue());
                    return binding;
                })
                .flatMap(shopItemSkuContractRepository::save)
                .then();
    }

    @Override
    public Mono<List<Long>> getItemContractIds(Long itemId) {
        return shopItemContractRepository.findEnabledByItemId(itemId)
                .map(ShopItemContract::getContractId)
                .collectList();
    }

    @Override
    public Mono<List<Long>> getSkuContractIds(Long skuId) {
        if (skuId == null) {
            return Mono.just(List.of());
        }
        return shopItemSkuContractRepository.findEnabledBySkuId(skuId)
                .map(ShopItemSkuContract::getContractId)
                .collectList();
    }

    @Override
    public Mono<Boolean> hasActiveContracts(Long itemId) {
        return hasActiveContracts(itemId, null);
    }

    @Override
    public Mono<Boolean> hasActiveContracts(Long itemId, Long skuId) {
        return getItemAgreementFiles(itemId, skuId).hasElements();
    }

    @Override
    public Flux<ShopContractAgreementVo> getItemAgreements(Long itemId) {
        return getItemAgreements(itemId, null);
    }

    @Override
    public Flux<ShopContractAgreementVo> getItemAgreements(Long itemId, Long skuId) {
        return getItemAgreements(itemId, skuId, List.of());
    }

    @Override
    public Flux<ShopContractAgreementVo> getItemAgreements(Long itemId, Long skuId,
                                                           List<Long> additionalContractIds) {
        return getItemAgreements(itemId, skuId, additionalContractIds, ShopContractLocalization.DEFAULT_LOCALE);
    }

    @Override
    public Flux<ShopContractAgreementVo> getItemAgreements(Long itemId, Long skuId,
                                                           List<Long> additionalContractIds, String locale) {
        return getContractIds(itemId, skuId, additionalContractIds)
                .flatMapMany(contractIds -> shopContractRepository.findEnabledByIds(contractIds).collectList()
                        .flatMapMany(contracts -> Flux.fromIterable(contracts)
                                .concatMap(contract -> shopContractFileRepository.findEnabledByContractId(contract.getId())
                                        .collectList()
                                        .map(files -> shopContractLocalization.selectLocaleVariants(files, locale))
                                        .filter(files -> !files.isEmpty())
                                        .map(files -> files.stream().map(this::toFileVo).toList())
                                        .map(files -> toAgreementVo(contract, files, locale)))));
    }

    @Override
    @Transactional
    public Mono<ShopContractSignatureProgressVo> agreeFile(Long itemId, ShopContractSignDto dto,
                                                           PortalUserVo buyer, ServerWebExchange exchange) {
        return agreeFile(itemId, null, dto, buyer, exchange);
    }

    @Override
    @Transactional
    public Mono<ShopContractSignatureProgressVo> agreeFile(Long itemId, Long skuId, ShopContractSignDto dto,
                                                           PortalUserVo buyer, ServerWebExchange exchange) {
        return agreeFile(itemId, skuId, List.of(), dto, buyer, exchange);
    }

    @Override
    @Transactional
    public Mono<ShopContractSignatureProgressVo> agreeFile(Long itemId, Long skuId,
                                                           List<Long> additionalContractIds,
                                                           ShopContractSignDto dto,
                                                           PortalUserVo buyer, ServerWebExchange exchange) {
        ShopAuthorizationUtils.requireLogin(buyer);
        String signToken = StringUtils.defaultIfBlank(dto.getSignToken(), IdGenerators.uuid32());
        return requireAgreementFile(itemId, skuId, additionalContractIds, dto.getContractId(), dto.getFileId())
                .flatMap(pair -> shopContractSignatureRepository
                        .findSignedFile(signToken, buyer.getId(), itemId, skuId, dto.getFileId())
                        .switchIfEmpty(Mono.defer(() -> createSignature(signToken, itemId, skuId, buyer, pair.contract(),
                                pair.file(), dto.getReadPercent(), exchange)))
                        .flatMap(signature -> {
                            signature.setReadPercent(normalizeReadPercent(dto.getReadPercent()));
                            signature.setStatus(ShopContractSignature.Status.AGREED);
                            signature.setAgreedTime(LocalDateTime.now());
                            signature.setClientIp(clientIp(exchange));
                            signature.setUserAgent(userAgent(exchange));
                            return shopContractSignatureRepository.save(signature);
                        }))
                .then(progress(itemId, skuId, additionalContractIds, buyer.getId(), signToken));
    }

    @Override
    public Mono<Void> requireSigned(Long itemId, PortalUserVo buyer, String signToken) {
        return requireSigned(itemId, null, buyer, signToken);
    }

    @Override
    public Mono<Void> requireSigned(Long itemId, Long skuId, PortalUserVo buyer, String signToken) {
        return requireSigned(itemId, skuId, List.of(), buyer, signToken);
    }

    @Override
    public Mono<Void> requireSigned(Long itemId, Long skuId, List<Long> additionalContractIds,
                                    PortalUserVo buyer, String signToken) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return getItemAgreementFiles(itemId, skuId, additionalContractIds).collectList().flatMap(files -> {
            if (files.isEmpty()) {
                return Mono.empty();
            }
            if (StringUtils.isBlank(signToken)) {
                return Mono.error(new BusinessException("CONTRACT_SIGNATURE_REQUIRED", "请先阅读并同意合同后再付款"));
            }
            return shopContractSignatureRepository.findAgreedByToken(signToken, buyer.getId(), itemId, skuId)
                    .collectList()
                    .map(signatures -> agreementProgress(files, signatures))
                    .flatMap(state -> state.completed()
                            ? Mono.empty()
                            : Mono.error(new BusinessException("CONTRACT_SIGNATURE_REQUIRED", "请先阅读并同意全部合同后再付款")));
        });
    }

    @Override
    @Transactional
    public Mono<Void> bindOrder(String signToken, String orderNo, Long itemId, Long buyerId) {
        return bindOrder(signToken, orderNo, itemId, null, buyerId);
    }

    @Override
    @Transactional
    public Mono<Void> bindOrder(String signToken, String orderNo, Long itemId, Long skuId, Long buyerId) {
        if (StringUtils.isAnyBlank(signToken, orderNo) || itemId == null || buyerId == null) {
            return Mono.empty();
        }
        return shopContractSignatureRepository.bindOrder(signToken, buyerId, itemId, skuId, orderNo).then();
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

    private Mono<AgreementFile> requireAgreementFile(Long itemId, Long skuId, List<Long> additionalContractIds,
                                                     Long contractId, Long fileId) {
        return getItemAgreementFiles(itemId, skuId, additionalContractIds)
                .filter(file -> Objects.equals(contractId, file.contract().getId())
                        && Objects.equals(fileId, file.file().getId()))
                .next()
                .switchIfEmpty(Mono.error(new BusinessException("CONTRACT_FILE_NOT_FOUND", "合同文件不存在或未绑定到商品")));
    }

    private Flux<AgreementFile> getItemAgreementFiles(Long itemId, Long skuId) {
        return getItemAgreementFiles(itemId, skuId, List.of());
    }

    private Flux<AgreementFile> getItemAgreementFiles(Long itemId, Long skuId, List<Long> additionalContractIds) {
        return getContractIds(itemId, skuId, additionalContractIds)
                .flatMapMany(contractIds -> {
                    if (contractIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return shopContractRepository.findEnabledByIds(contractIds)
                            .flatMap(contract -> shopContractFileRepository.findEnabledByContractId(contract.getId())
                                    .collectList()
                                    .flatMapMany(files -> Flux.fromIterable(
                                            shopContractLocalization.currentVariants(files)))
                                    .map(file -> new AgreementFile(contract, file)));
                });
    }

    private Mono<List<Long>> getContractIds(Long itemId, Long skuId) {
        return getContractIds(itemId, skuId, List.of());
    }

    private Mono<List<Long>> getContractIds(Long itemId, Long skuId, List<Long> additionalContractIds) {
        Mono<List<Long>> configured = skuId == null ? getItemContractIds(itemId) : getSkuContractIds(skuId);
        return configured.map(contractIds -> {
            Set<Long> merged = new LinkedHashSet<>(contractIds);
            merged.addAll(normalizeIds(additionalContractIds));
            return new java.util.ArrayList<>(merged);
        });
    }

    private Mono<ShopContractSignature> createSignature(String signToken, Long itemId, Long skuId, PortalUserVo buyer,
                                                        ShopContract contract, ShopContractFile file,
                                                        Integer readPercent, ServerWebExchange exchange) {
        ShopContractLocalization.LocalizedContract localized = shopContractLocalization.localize(
                contract, file.getLocale());
        ShopContractSignature signature = new ShopContractSignature();
        signature.setSignToken(signToken);
        signature.setItemId(itemId);
        signature.setSkuId(skuId);
        signature.setBuyerId(buyer.getId());
        signature.setContractId(contract.getId());
        signature.setContractFileId(file.getId());
        signature.setContractName(localized.name());
        signature.setContractType(contract.getType() == null ? null : contract.getType().name());
        signature.setContractLocale(shopContractLocalization.normalizeLocale(file.getLocale()));
        signature.setDocumentKey(shopContractLocalization.documentKey(file));
        signature.setFileName(file.getFileName());
        signature.setFileUrl(file.getFileUrl());
        signature.setFileHash(file.getFileHash());
        signature.setFileVersion(shopContractLocalization.fileVersion(file));
        signature.setReadPercent(normalizeReadPercent(readPercent));
        signature.setStatus(ShopContractSignature.Status.AGREED);
        signature.setAgreedTime(LocalDateTime.now());
        signature.setClientIp(clientIp(exchange));
        signature.setUserAgent(userAgent(exchange));
        return Mono.just(signature);
    }

    private Mono<ShopContractSignatureProgressVo> progress(Long itemId, Long skuId,
                                                           List<Long> additionalContractIds,
                                                           Long buyerId, String signToken) {
        return getItemAgreementFiles(itemId, skuId, additionalContractIds).collectList()
                .zipWith(shopContractSignatureRepository.findAgreedByToken(signToken, buyerId, itemId, skuId)
                        .collectList())
                .map(tuple -> {
                    AgreementProgress state = agreementProgress(tuple.getT1(), tuple.getT2());
                    ShopContractSignatureProgressVo vo = new ShopContractSignatureProgressVo();
                    vo.setSignToken(signToken);
                    vo.setTotalCount(state.totalCount());
                    vo.setAgreedCount(state.agreedCount());
                    vo.setCompleted(state.completed());
                    return vo;
                });
    }

    private ShopContractFile toFile(Long contractId, String locale, String documentKey, int version,
                                    FileMetadata metadata) {
        ShopContractFile file = new ShopContractFile();
        file.setContractId(contractId);
        file.setDocumentKey(documentKey);
        file.setLocale(locale);
        file.setFileName(metadata.getOriginalFilename());
        file.setFileUrl(metadata.getUrl());
        file.setFileHash(metadata.getHash());
        file.setFileVersion(version);
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
                    vo.setI18n(shopContractLocalization.deserialize(contract.getI18n()));
                    vo.setEnabled(contract.getEnabled());
                    vo.setSort(contract.getSort());
                    vo.setFiles(files);
                    vo.setCreateTime(contract.getCreateTime());
                    vo.setUpdateTime(contract.getUpdateTime());
                    return vo;
                });
    }

    private ShopContractAgreementVo toAgreementVo(ShopContract contract, List<ShopContractFileVo> files,
                                                  String locale) {
        ShopContractLocalization.LocalizedContract localized = shopContractLocalization.localize(contract, locale);
        ShopContractAgreementVo vo = new ShopContractAgreementVo();
        vo.setId(contract.getId());
        vo.setName(localized.name());
        vo.setType(contract.getType() == null ? null : contract.getType().name());
        vo.setTypeName(localized.typeName());
        vo.setDescription(localized.description());
        vo.setTags(localized.tags());
        vo.setLocale(localized.locale());
        vo.setFiles(files);
        return vo;
    }

    private ShopContractFileVo toFileVo(ShopContractFile file) {
        ShopContractFileVo vo = new ShopContractFileVo();
        vo.setId(file.getId());
        vo.setContractId(file.getContractId());
        vo.setDocumentKey(shopContractLocalization.documentKey(file));
        vo.setLocale(shopContractLocalization.normalizeLocale(file.getLocale()));
        vo.setFileName(file.getFileName());
        vo.setFileUrl(file.getFileUrl());
        vo.setFileHash(file.getFileHash());
        vo.setFileVersion(shopContractLocalization.fileVersion(file));
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
        vo.setSkuId(signature.getSkuId());
        vo.setBuyerId(signature.getBuyerId());
        vo.setContractId(signature.getContractId());
        vo.setContractFileId(signature.getContractFileId());
        vo.setContractName(signature.getContractName());
        vo.setContractType(signature.getContractType());
        vo.setContractLocale(signature.getContractLocale());
        vo.setDocumentKey(signature.getDocumentKey());
        vo.setFileName(signature.getFileName());
        vo.setFileUrl(signature.getFileUrl());
        vo.setFileHash(signature.getFileHash());
        vo.setFileVersion(signature.getFileVersion());
        vo.setReadPercent(signature.getReadPercent());
        vo.setStatus(signature.getStatus() == null ? null : signature.getStatus().name());
        vo.setAgreedTime(signature.getAgreedTime());
        vo.setClientIp(signature.getClientIp());
        vo.setUserAgent(signature.getUserAgent());
        vo.setCreateTime(signature.getCreateTime());
        return vo;
    }

    private Mono<Void> retirePreviousVersion(List<ShopContractFile> files, String documentKey, int version) {
        return Flux.fromIterable(files)
                .filter(file -> Boolean.TRUE.equals(file.getEnabled()))
                .filter(file -> Objects.equals(documentKey, shopContractLocalization.documentKey(file)))
                .filter(file -> shopContractLocalization.fileVersion(file) < version)
                .concatMap(file -> {
                    file.setEnabled(false);
                    return shopContractFileRepository.save(file);
                })
                .then();
    }

    private Mono<Void> disableConflictingVariant(List<ShopContractFile> files, ShopContractFile target) {
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            return Mono.empty();
        }
        String targetKey = shopContractLocalization.documentKey(target);
        String targetLocale = shopContractLocalization.normalizeLocale(target.getLocale());
        int targetVersion = shopContractLocalization.fileVersion(target);
        return Flux.fromIterable(files)
                .filter(file -> !Objects.equals(file.getId(), target.getId()))
                .filter(file -> Boolean.TRUE.equals(file.getEnabled()))
                .filter(file -> Objects.equals(targetKey, shopContractLocalization.documentKey(file)))
                .filter(file -> targetVersion == shopContractLocalization.fileVersion(file))
                .filter(file -> Objects.equals(targetLocale,
                        shopContractLocalization.normalizeLocale(file.getLocale())))
                .concatMap(file -> {
                    file.setEnabled(false);
                    return shopContractFileRepository.save(file);
                })
                .then();
    }

    private AgreementProgress agreementProgress(List<AgreementFile> files,
                                                List<ShopContractSignature> signatures) {
        Map<Long, AgreementDocument> documentsByFileId = files.stream()
                .collect(Collectors.toMap(
                        agreement -> agreement.file().getId(),
                        this::agreementDocument,
                        (first, ignored) -> first
                ));
        Set<AgreementDocument> required = new LinkedHashSet<>(documentsByFileId.values());
        Set<AgreementDocument> agreed = signatures.stream()
                .map(signature -> signatureDocument(signature, documentsByFileId))
                .filter(Objects::nonNull)
                .filter(required::contains)
                .collect(Collectors.toSet());
        return new AgreementProgress(required.size(), agreed.size(), agreed.containsAll(required));
    }

    private AgreementDocument agreementDocument(AgreementFile agreement) {
        ShopContractFile file = agreement.file();
        return new AgreementDocument(
                agreement.contract().getId(),
                shopContractLocalization.documentKey(file),
                shopContractLocalization.fileVersion(file)
        );
    }

    private AgreementDocument signatureDocument(ShopContractSignature signature,
                                                Map<Long, AgreementDocument> documentsByFileId) {
        if (StringUtils.isBlank(signature.getDocumentKey())) {
            return documentsByFileId.get(signature.getContractFileId());
        }
        return new AgreementDocument(
                signature.getContractId(),
                signature.getDocumentKey(),
                signature.getFileVersion() == null ? 1 : signature.getFileVersion()
        );
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

    private record AgreementDocument(Long contractId, String documentKey, int version) {
    }

    private record AgreementProgress(int totalCount, int agreedCount, boolean completed) {
    }
}
