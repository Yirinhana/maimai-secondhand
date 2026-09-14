package com.maimai.payment;

import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.config.MaimaiProperties;
import com.maimai.payment.domain.PaymentRequest;
import com.maimai.payment.repo.PaymentRequestRepository;
import com.maimai.payment.service.PaymentService;
import com.maimai.payment.service.WechatPaymentChannel;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentEnvironmentTest {
    final OrderRepository orders=mock(OrderRepository.class);
    final PaymentRequestRepository payments=mock(PaymentRequestRepository.class);
    final MaimaiProperties properties=new MaimaiProperties();
    final MockEnvironment environment=new MockEnvironment();

    @BeforeEach void setup() {
        var principal=new AuthenticatedUser(1L,"buyer@example.invalid","买家",Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        var order=new Order();order.setId(12L);order.setBuyerId(1L);order.setOrderNo("ORDER-LOCAL");
        order.setFulfillmentStatus(Order.FulfillmentStatus.PENDING_PAYMENT);order.setPayStatus(Order.PayStatus.UNPAID);
        order.setExpiresAt(Instant.now().plusSeconds(600));
        when(orders.lockByOrderNo("ORDER-LOCAL")).thenReturn(Optional.of(order));
        var payment=new PaymentRequest();payment.setPayNo("MOCK-OLD");payment.setOrderId(12L);
        payment.setChannel(PaymentRequest.Channel.MOCK_LOCAL);payment.setSimulated(true);payment.setStatus(PaymentRequest.Status.CREATED);
        when(payments.findTopByOrderIdOrderByCreatedAtDesc(12L)).thenReturn(Optional.of(payment));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    PaymentService service(){return new PaymentService(orders,payments,properties,new WechatPaymentChannel(properties),environment);}
    void refused() {
        assertThatThrownBy(()->service().pay("ORDER-LOCAL")).isInstanceOfSatisfying(BizException.class,e->{
            assertThat(e.getCode()).isEqualTo("PAYMENT_NOT_CONFIGURED");assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
        verify(payments,never()).save(any());
    }
    @Test void productionCannotReuseOldMockPayment(){environment.setActiveProfiles("prod");properties.getPayment().setChannel("mock");refused();}
    @Test void switchingToWechatCannotReuseOldMockPayment(){environment.setActiveProfiles("local");properties.getPayment().setChannel("wechat");refused();}
    @Test void configuredButUnimplementedWechatStillRefuses() {
        environment.setActiveProfiles("prod");properties.getPayment().setChannel("wechat");
        var wechat=properties.getPayment().getWechat();wechat.setMchId("test-id");wechat.setAppId("test-app");wechat.setApiKey("test-key");wechat.setCertPath("test-cert");
        refused();
    }
    @Test void allowedLocalMockReuseRemainsIdempotent() {
        environment.setActiveProfiles("local");properties.getPayment().setChannel("mock");
        var response=service().pay("ORDER-LOCAL");assertThat(response.payNo()).isEqualTo("MOCK-OLD");assertThat(response.simulated()).isTrue();
        verify(payments,never()).save(any());
    }
}
