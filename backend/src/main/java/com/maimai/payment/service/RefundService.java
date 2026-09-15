package com.maimai.payment.service;

import com.maimai.common.BizException;
import com.maimai.common.FeeCalculator;
import com.maimai.common.NoGenerator;
import com.maimai.notification.NotificationService;
import com.maimai.payment.domain.LedgerEntry;
import com.maimai.payment.domain.PaymentRequest;
import com.maimai.payment.domain.Refund;
import com.maimai.payment.repo.LedgerEntryRepository;
import com.maimai.payment.repo.PaymentRequestRepository;
import com.maimai.payment.repo.RefundRepository;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 退款引擎：商品款/运费分开退款，连续退款平台费按剩余商品款重算（FeeCalculator 唯一入口），
 * 累计 SUCCESS 退款严禁超退。模拟渠道走完整状态流转；微信渠道真实退款待开通，一律拒绝。
 */
@Service
@Transactional
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final org.springframework.core.env.Environment environment;
    private final com.maimai.payment.finance.FinanceService finance;
    private final com.maimai.trade.service.RefundFulfillmentService refundFulfillment;

    public RefundService(RefundRepository refundRepository,
                         PaymentRequestRepository paymentRequestRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         OrderRepository orderRepository,
                         NotificationService notificationService, org.springframework.core.env.Environment environment,
                         com.maimai.payment.finance.FinanceService finance,
                         com.maimai.trade.service.RefundFulfillmentService refundFulfillment) {
        this.refundRepository = refundRepository;
        this.paymentRequestRepository = paymentRequestRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.environment = environment;
        this.finance = finance;
        this.refundFulfillment = refundFulfillment;
    }

    /**
     * 创建并执行一笔退款。在调用方事务内执行，失败整体回滚。
     *
     * @param order       订单（调用方已做业务校验）
     * @param aftersaleId 关联售后单，可为 null（后台直接处理）
     * @param goodsCents  本次退商品款（分）
     * @param freightCents 本次退运费（分）
     */
    public Refund createRefund(Order order, Long aftersaleId, long goodsCents, long freightCents) {
        Order locked = orderRepository.lockById(order.getId())
                .orElseThrow(() -> BizException.notFound("订单不存在"));
        locked.requireLiveRecord();
        if (locked.getPayStatus() != Order.PayStatus.PAID) {
            throw BizException.conflict("REFUND_NOT_PAID", "订单未支付成功，不能退款");
        }
        if (goodsCents < 0 || freightCents < 0) {
            throw BizException.badRequest("REFUND_AMOUNT_INVALID", "退款金额不能为负");
        }
        if (goodsCents == 0 && freightCents == 0) {
            throw BizException.badRequest("REFUND_AMOUNT_INVALID", "本次退款金额必须大于 0");
        }
        List<Refund> history = refundRepository.findByOrderId(locked.getId());
        for (Refund previous : history) {
            if (aftersaleId != null && aftersaleId.equals(previous.getAftersaleId())
                    && previous.getStatus() == Refund.Status.SUCCESS) {
                if (previous.getGoodsRefundCents() != goodsCents || previous.getFreightRefundCents() != freightCents) {
                    throw BizException.conflict("REFUND_REPLAY_MISMATCH", "该售后已按其他金额完成退款");
                }
                return previous;
            }
        }
        long refundedGoods = 0;
        long refundedFreight = 0;
        long refundedFee = 0;
        for (Refund past : history) {
            if (past.getStatus() == Refund.Status.SUCCESS) {
                refundedGoods += past.getGoodsRefundCents();
                refundedFreight += past.getFreightRefundCents();
                refundedFee += past.getPlatformFeeRefundCents();
            }
        }
        if (goodsCents > locked.getGoodsAmountCents() - refundedGoods) {
            throw BizException.conflict("REFUND_EXCEEDED", "累计商品退款将超过商品成交额，已拒绝");
        }
        if (freightCents > locked.getFreightCents() - refundedFreight) {
            throw BizException.conflict("REFUND_EXCEEDED", "累计运费退款将超过订单运费，已拒绝");
        }
        long feeDelta = FeeCalculator.refundableFeeDelta(locked.getGoodsAmountCents(),
                refundedGoods + goodsCents, locked.getPlatformFeeCents(), refundedFee);

        PaymentRequest payment = paymentRequestRepository
                .findTopByOrderIdOrderByCreatedAtDesc(locked.getId())
                .orElseThrow(() -> BizException.conflict("REFUND_NO_PAYMENT", "订单无支付单，无法退款"));
        if (payment.getStatus() != PaymentRequest.Status.PAID) {
            throw BizException.conflict("REFUND_NOT_PAID", "支付流水尚未确认成功");
        }

        Refund refund = new Refund();
        refund.setRefundNo(NoGenerator.next("MR"));
        refund.setOrderId(locked.getId());
        refund.setAftersaleId(aftersaleId);
        refund.setGoodsRefundCents(goodsCents);
        refund.setFreightRefundCents(freightCents);
        refund.setPlatformFeeRefundCents(feeDelta);
        refund.setChannel(payment.getChannel().name());
        refund.setSimulated(payment.isSimulated());
        refundRepository.save(refund);

        execute(refund);

        // 更新订单退款状态：商品款与运费均累计退满 → FULL，否则 PARTIAL
        boolean goodsFull = refundedGoods + goodsCents >= locked.getGoodsAmountCents();
        boolean freightFull = refundedFreight + freightCents >= locked.getFreightCents();
        locked.setRefundStatus(goodsFull && freightFull
                ? Order.RefundStatus.FULL : Order.RefundStatus.PARTIAL);
        locked.setUpdatedAt(Instant.now());
        refundFulfillment.closeUnshippedFullRefund(locked);
        orderRepository.saveAndFlush(locked);
        finance.captureExpectedAllocation(locked.getId());

        if (goodsCents > 0) {
            saveLedger(locked.getId(), "REFUND_GOODS", goodsCents, refund.getId());
        }
        if (freightCents > 0) {
            saveLedger(locked.getId(), "REFUND_FREIGHT", freightCents, refund.getId());
        }
        if (feeDelta > 0) {
            saveLedger(locked.getId(), "REFUND_PLATFORM_FEE", feeDelta, refund.getId());
        }
        notificationService.notify(locked.getBuyerId(), "REFUND", "退款完成",
                "退款单 " + refund.getRefundNo() + " 已完成：商品款 " + goodsCents + " 分，运费 "
                        + freightCents + " 分，平台服务费 " + feeDelta + " 分"
                        + (refund.isSimulated() ? "（本地隔离环境模拟退款，未发生真实资金）" : ""));
        return refund;
    }

    /** 执行退款：模拟渠道走完整 REQUESTED→PROCESSING→SUCCESS 流转；微信渠道拒绝（待真实接入）。 */
    private void execute(Refund refund) {
        if (!PaymentRequest.Channel.MOCK_LOCAL.name().equals(refund.getChannel()) || !refund.isSimulated()
                || !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test"))) {
            throw new BizException("REFUND_NOT_CONFIGURED", "微信退款真实接入待渠道开通，已拒绝",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        refund.setStatus(Refund.Status.PROCESSING);
        refund.setUpdatedAt(Instant.now());
        refundRepository.save(refund);
        refund.setStatus(Refund.Status.SUCCESS);
        refund.setUpdatedAt(Instant.now());
        refundRepository.save(refund);
    }

    private void saveLedger(Long orderId, String entryType, long amountCents, Long refundId) {
        LedgerEntry entry = new LedgerEntry();
        entry.setOrderId(orderId);
        entry.setEntryType(entryType);
        entry.setAmountCents(amountCents);
        entry.setRefType("REFUND");
        entry.setRefId(refundId);
        ledgerEntryRepository.save(entry);
    }
}
