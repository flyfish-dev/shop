package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.shop.repository.ShopTransactionRepository;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.impl.ShopOrderServiceImpl;
import group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuSigner;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuNotifyDto;
import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties;
import group.flyfish.dev.shop.service.support.stripe.bean.StripeCheckoutSessionDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static group.flyfish.dev.shop.donation.DonationThankYouTemplateTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationPaymentHookTest {

    @Mock ShopOrderRepository orders;
    @Mock ShopItemRepository items;
    @Mock ShopTransactionRepository transactions;
    @Mock ShopCouponService coupons;
    @Mock AuthUserGateway users;
    @Mock DonationThankYouService thanks;
    @Mock H5ZhiFuProperties h5;
    @InjectMocks ShopOrderServiceImpl service;

    @Test
    void h5PaymentRecordsThankYouAndPreservesNoDeliveryCompletion() {
        ShopOrder order = paidOrder();
        order.setPaidTime(null);
        order.setStatus(ShopOrder.Status.PAYING);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.WAITING);
        order.setItemId(20L);
        order.setCount(1);
        when(orders.findByOrderNo(order.getOrderNo())).thenReturn(Mono.just(order));
        when(orders.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(transactions.findByCode("T1")).thenReturn(Mono.empty());
        when(transactions.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(coupons.markUsed(null)).thenReturn(Mono.empty());
        ShopItem item = new ShopItem();
        item.setId(20L);
        item.setType(ShopItem.Type.DONATION);
        when(items.findById(20L)).thenReturn(Mono.just(item));
        when(items.save(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(users.getById(100L)).thenReturn(Mono.just(donor("donor@example.test")));
        when(thanks.recordPaid(order)).thenAnswer(i -> {
            assertThat(order.getPaidTime()).isNotNull();
            return Mono.empty();
        });
        var notify = h5Notification();
        StepVerifier.create(service.handlePaymentNotify(notify)).verifyComplete();
        assertThat(order.getStatus()).isEqualTo(ShopOrder.Status.DELIVERED);
        assertThat(order.getDeliveryStatus()).isEqualTo(ShopOrder.DeliveryStatus.SUCCESS);
        StepVerifier.create(service.handlePaymentNotify(notify)).verifyComplete();
        verify(transactions, times(1)).save(any());
        // 通知模块按订单去重，重复回调不会漏掉已付款但尚未入队的通知。
        verify(thanks, times(2)).recordPaid(order);
    }

    @Test
    void stripeOnlyRecordsSuccessfullyPaidSessions() {
        ShopOrder order = paidOrder();
        order.setPaymentProvider("stripe");
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.SUCCESS);
        when(orders.findByOrderNo(order.getOrderNo())).thenReturn(Mono.just(order));
        when(thanks.recordPaid(order)).thenReturn(Mono.empty());
        var session = new StripeCheckoutSessionDto();
        session.setId("cs_test_donation");
        session.setClientReferenceId(order.getOrderNo());
        session.setCurrency("cny");
        session.setAmountTotal(1888L);
        session.setPaymentStatus("unpaid");
        StepVerifier.create(service.handleStripeCheckoutSession(session, "checkout.session.completed")).verifyComplete();
        verifyNoInteractions(thanks);
        session.setPaymentStatus("paid");
        StepVerifier.create(service.handleStripeCheckoutSession(session, "checkout.session.async_payment_succeeded"))
                .verifyComplete();
        verify(thanks).recordPaid(order);
    }

    @Test
    void invalidSignatureCannotEnqueueThanks() {
        var notify = h5Notification();
        notify.setSign("invalid");
        assertThatThrownBy(() -> service.handlePaymentNotify(notify)).hasMessageContaining("签名");
        verifyNoInteractions(thanks, orders);
    }

    private H5ZhiFuNotifyDto h5Notification() {
        when(h5.getKey()).thenReturn("unit-test-only");
        when(h5.getAppId()).thenReturn(123L);
        var notify = new H5ZhiFuNotifyDto();
        notify.setAppId(123L);
        notify.setOutTradeNo("FFTHANKS1");
        notify.setTradeNo("T1");
        notify.setAmount(1888);
        notify.setSign(H5ZhiFuSigner.sign(notify, "unit-test-only"));
        return notify;
    }
}
