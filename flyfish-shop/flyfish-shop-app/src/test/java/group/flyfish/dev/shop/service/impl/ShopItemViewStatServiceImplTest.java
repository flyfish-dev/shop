package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.repository.ShopItemViewStatRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopItemViewStatServiceImplTest {

    @Test
    void recordViewOnlyWritesMemoryAndReturnsMergedCount() {
        ShopItemViewStatRepository repository = mock(ShopItemViewStatRepository.class);
        when(repository.findViewCountByItemId(7L)).thenReturn(Mono.just(12L));
        ShopItemViewStatServiceImpl service = new ShopItemViewStatServiceImpl(repository);

        StepVerifier.create(service.recordAndGetViewCount(7L))
                .expectNext(13L)
                .verifyComplete();

        verify(repository, never()).increaseViewCount(anyLong(), anyLong());
        verify(repository, never()).insertInitialViewCount(anyLong(), anyLong());
    }

    @Test
    void flushPendingViewsMergesDeltaByItem() {
        ShopItemViewStatRepository repository = mock(ShopItemViewStatRepository.class);
        when(repository.findViewCountByItemId(7L)).thenReturn(Mono.just(0L));
        when(repository.increaseViewCount(7L, 2L)).thenReturn(Mono.just(1));
        ShopItemViewStatServiceImpl service = new ShopItemViewStatServiceImpl(repository);

        StepVerifier.create(service.recordAndGetViewCount(7L)).expectNext(1L).verifyComplete();
        StepVerifier.create(service.recordAndGetViewCount(7L)).expectNext(2L).verifyComplete();
        StepVerifier.create(service.flushPendingViews()).verifyComplete();

        verify(repository).increaseViewCount(7L, 2L);
    }

    @Test
    void flushPendingViewsInsertsWhenStatRowDoesNotExist() {
        ShopItemViewStatRepository repository = mock(ShopItemViewStatRepository.class);
        when(repository.findViewCountByItemId(7L)).thenReturn(Mono.empty());
        when(repository.increaseViewCount(7L, 1L)).thenReturn(Mono.just(0));
        when(repository.insertInitialViewCount(7L, 1L)).thenReturn(Mono.just(1));
        ShopItemViewStatServiceImpl service = new ShopItemViewStatServiceImpl(repository);

        StepVerifier.create(service.recordAndGetViewCount(7L)).expectNext(1L).verifyComplete();
        StepVerifier.create(service.flushPendingViews()).verifyComplete();

        verify(repository).insertInitialViewCount(7L, 1L);
    }
}
