package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.shop.domain.po.ShopContract;
import group.flyfish.dev.shop.domain.po.ShopContractFile;
import group.flyfish.dev.shop.domain.po.ShopContractSignature;
import group.flyfish.dev.shop.domain.po.ShopItemContract;
import group.flyfish.dev.shop.repository.ShopContractFileRepository;
import group.flyfish.dev.shop.repository.ShopContractRepository;
import group.flyfish.dev.shop.repository.ShopContractSignatureRepository;
import group.flyfish.dev.shop.repository.ShopItemContractRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuContractRepository;
import group.flyfish.dev.shop.service.support.ShopContractLocalization;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopContractServiceImplTest {

    @Test
    void noContractItemDoesNotRequireSignatureToken() {
        ShopItemContractRepository itemContractRepository = mock(ShopItemContractRepository.class);
        when(itemContractRepository.findEnabledByItemId(100L)).thenReturn(Flux.empty());

        ShopContractServiceImpl service = service(
                mock(ShopContractRepository.class),
                mock(ShopContractFileRepository.class),
                itemContractRepository,
                mock(ShopContractSignatureRepository.class)
        );

        StepVerifier.create(service.requireSigned(100L, buyer(), null))
                .verifyComplete();
    }

    @Test
    void contractItemRequiresSignatureTokenBeforePayment() {
        ShopContractRepository contractRepository = mock(ShopContractRepository.class);
        ShopContractFileRepository fileRepository = mock(ShopContractFileRepository.class);
        ShopItemContractRepository itemContractRepository = mock(ShopItemContractRepository.class);

        ShopItemContract binding = new ShopItemContract();
        binding.setItemId(100L);
        binding.setContractId(10L);
        binding.setEnabled(true);
        ShopContract contract = new ShopContract();
        contract.setId(10L);
        contract.setEnabled(true);
        contract.setName("商业授权合同");
        contract.setType(ShopContract.Type.SOFTWARE_LICENSE);
        ShopContractFile file = new ShopContractFile();
        file.setId(20L);
        file.setContractId(10L);
        file.setEnabled(true);
        file.setFileName("license.pdf");
        file.setFileUrl("/images/license.pdf");

        when(itemContractRepository.findEnabledByItemId(100L)).thenReturn(Flux.just(binding));
        when(contractRepository.findEnabledByIds(List.of(10L))).thenReturn(Flux.just(contract));
        when(fileRepository.findEnabledByContractId(10L)).thenReturn(Flux.just(file));

        ShopContractServiceImpl service = service(
                contractRepository,
                fileRepository,
                itemContractRepository,
                mock(ShopContractSignatureRepository.class)
        );

        StepVerifier.create(service.requireSigned(100L, buyer(), null))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("CONTRACT_SIGNATURE_REQUIRED", exception.getCode());
                })
                .verify();
    }

    @Test
    void oneLanguageSignatureCoversTheSameLogicalDocumentOnlyOnce() {
        ShopContractRepository contractRepository = mock(ShopContractRepository.class);
        ShopContractFileRepository fileRepository = mock(ShopContractFileRepository.class);
        ShopItemContractRepository itemContractRepository = mock(ShopItemContractRepository.class);
        ShopContractSignatureRepository signatureRepository = mock(ShopContractSignatureRepository.class);

        ShopItemContract binding = new ShopItemContract();
        binding.setItemId(100L);
        binding.setContractId(10L);
        binding.setEnabled(true);
        ShopContract contract = new ShopContract();
        contract.setId(10L);
        contract.setEnabled(true);
        contract.setName("商业授权合同");
        contract.setType(ShopContract.Type.SOFTWARE_LICENSE);
        ShopContractFile chinese = contractFile(20L, "zh-CN", 2);
        ShopContractFile english = contractFile(21L, "en-US", 2);
        ShopContractSignature signature = new ShopContractSignature();
        signature.setContractId(10L);
        signature.setContractFileId(21L);
        signature.setDocumentKey("purchase-agreement");
        signature.setFileVersion(2);

        when(itemContractRepository.findEnabledByItemId(100L)).thenReturn(Flux.just(binding));
        when(contractRepository.findEnabledByIds(List.of(10L))).thenReturn(Flux.just(contract));
        when(fileRepository.findEnabledByContractId(10L)).thenReturn(Flux.just(chinese, english));
        when(signatureRepository.findAgreedByToken("signed", 1L, 100L, null))
                .thenReturn(Flux.just(signature));

        ShopContractServiceImpl service = service(
                contractRepository, fileRepository, itemContractRepository, signatureRepository);

        StepVerifier.create(service.requireSigned(100L, buyer(), "signed"))
                .verifyComplete();
    }

    @Test
    void oldRevisionSignatureCannotAuthorizeNewContractRevision() {
        ShopContractRepository contractRepository = mock(ShopContractRepository.class);
        ShopContractFileRepository fileRepository = mock(ShopContractFileRepository.class);
        ShopItemContractRepository itemContractRepository = mock(ShopItemContractRepository.class);
        ShopContractSignatureRepository signatureRepository = mock(ShopContractSignatureRepository.class);

        ShopItemContract binding = new ShopItemContract();
        binding.setItemId(100L);
        binding.setContractId(10L);
        binding.setEnabled(true);
        ShopContract contract = new ShopContract();
        contract.setId(10L);
        contract.setEnabled(true);
        ShopContractFile current = contractFile(22L, "en-US", 3);
        ShopContractSignature oldSignature = new ShopContractSignature();
        oldSignature.setContractId(10L);
        oldSignature.setContractFileId(21L);
        oldSignature.setDocumentKey("purchase-agreement");
        oldSignature.setFileVersion(2);

        when(itemContractRepository.findEnabledByItemId(100L)).thenReturn(Flux.just(binding));
        when(contractRepository.findEnabledByIds(List.of(10L))).thenReturn(Flux.just(contract));
        when(fileRepository.findEnabledByContractId(10L)).thenReturn(Flux.just(current));
        when(signatureRepository.findAgreedByToken("stale", 1L, 100L, null))
                .thenReturn(Flux.just(oldSignature));

        ShopContractServiceImpl service = service(
                contractRepository, fileRepository, itemContractRepository, signatureRepository);

        StepVerifier.create(service.requireSigned(100L, buyer(), "stale"))
                .expectErrorMatches(error -> error instanceof BusinessException exception
                        && "CONTRACT_SIGNATURE_REQUIRED".equals(exception.getCode()))
                .verify();
    }

    private ShopContractFile contractFile(Long id, String locale, int version) {
        ShopContractFile file = new ShopContractFile();
        file.setId(id);
        file.setContractId(10L);
        file.setDocumentKey("purchase-agreement");
        file.setLocale(locale);
        file.setFileVersion(version);
        file.setEnabled(true);
        file.setFileName("purchase-agreement.docx");
        file.setFileUrl("/images/purchase-agreement.docx");
        return file;
    }

    private ShopContractServiceImpl service(ShopContractRepository contractRepository,
                                            ShopContractFileRepository fileRepository,
                                            ShopItemContractRepository itemContractRepository,
                                            ShopContractSignatureRepository signatureRepository) {
        return new ShopContractServiceImpl(
                contractRepository,
                fileRepository,
                itemContractRepository,
                mock(ShopItemSkuContractRepository.class),
                signatureRepository,
                mock(UploadService.class),
                new ShopContractLocalization()
        );
    }

    private PortalUserVo buyer() {
        PortalUserVo user = new PortalUserVo();
        user.setId(1L);
        return user;
    }
}
