package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.shop.domain.po.ShopContract;
import group.flyfish.dev.shop.domain.po.ShopContractFile;
import group.flyfish.dev.shop.domain.po.ShopItemContract;
import group.flyfish.dev.shop.repository.ShopContractFileRepository;
import group.flyfish.dev.shop.repository.ShopContractRepository;
import group.flyfish.dev.shop.repository.ShopContractSignatureRepository;
import group.flyfish.dev.shop.repository.ShopItemContractRepository;
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

    private ShopContractServiceImpl service(ShopContractRepository contractRepository,
                                            ShopContractFileRepository fileRepository,
                                            ShopItemContractRepository itemContractRepository,
                                            ShopContractSignatureRepository signatureRepository) {
        return new ShopContractServiceImpl(
                contractRepository,
                fileRepository,
                itemContractRepository,
                signatureRepository,
                mock(UploadService.class)
        );
    }

    private PortalUserVo buyer() {
        PortalUserVo user = new PortalUserVo();
        user.setId(1L);
        return user;
    }
}
