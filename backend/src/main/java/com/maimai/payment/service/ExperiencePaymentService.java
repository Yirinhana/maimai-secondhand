package com.maimai.payment.service;

import com.maimai.common.BizException;
import com.maimai.common.NoGenerator;
import com.maimai.common.security.SecurityUtils;
import com.maimai.config.MaimaiProperties;
import com.maimai.payment.domain.PaymentRequest;
import com.maimai.payment.dto.ExperiencePaymentDtos.*;
import com.maimai.payment.repo.PaymentRequestRepository;
import com.maimai.trade.api.TradeOrderOps;
import com.maimai.trade.domain.Order;
import com.maimai.trade.repo.OrderRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/** Persisted no-money channel, independently guarded from real payments and local dev callbacks. */
@Service
@Transactional
public class ExperiencePaymentService {
    private final OrderRepository orders;
    private final PaymentRequestRepository payments;
    private final TradeOrderOps trade;
    private final JdbcTemplate db;
    private final MaimaiProperties properties;
    private final SecureRandom random = new SecureRandom();

    public ExperiencePaymentService(OrderRepository orders, PaymentRequestRepository payments,
                                    TradeOrderOps trade, JdbcTemplate db, MaimaiProperties properties) {
        this.orders = orders; this.payments = payments; this.trade = trade; this.db = db; this.properties = properties;
    }

    public Session create(String orderNo) {
        Order order = orders.lockByOrderNo(orderNo).orElseThrow(() -> BizException.notFound("订单不存在"));
        requireBuyerExperience(order);
        requirePayable(order);
        PaymentRequest payment = payments.findTopByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        if (payment != null) requireChannel(payment);
        if (payment == null || payment.getStatus() != PaymentRequest.Status.CREATED) {
            payment = new PaymentRequest();
            payment.setOrderId(order.getId()); payment.setPayNo(NoGenerator.next("EP"));
            payment.setAmountCents(order.getTotalCents());
            payment.setChannel(PaymentRequest.Channel.EXPERIENCE_QR); payment.setSimulated(true);
            payments.saveAndFlush(payment);
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            db.update("INSERT INTO experience_payment_sessions(token,payment_id) VALUES(?,?)",
                    HexFormat.of().formatHex(bytes), payment.getId());
        }
        String token = db.queryForObject("SELECT token FROM experience_payment_sessions WHERE payment_id=?",
                String.class, payment.getId());
        return view(token, order, payment);
    }

    @Transactional(readOnly = true)
    public Session get(String token) {
        PaymentRequest payment = find(token);
        Order order = orders.findById(payment.getOrderId()).orElseThrow(() -> BizException.notFound("订单不存在"));
        requireBuyerExperience(order); requireChannel(payment);
        return view(token, order, payment);
    }

    public Session finish(String token, Result result) {
        validateToken(token);
        // Read identifiers only before locking; never retain a stale payment entity in the JPA context.
        var pointers = db.query("SELECT p.order_id,p.pay_no FROM experience_payment_sessions s JOIN payment_requests p ON p.id=s.payment_id WHERE s.token=?",
                (rs, row) -> new Pointer(rs.getLong("order_id"), rs.getString("pay_no")), token);
        if (pointers.isEmpty()) throw BizException.notFound("体验收银页不存在");
        Pointer found = pointers.getFirst();
        // Same lock order as cancellation/refund: order first, payment second.
        Order order = orders.lockById(found.orderId()).orElseThrow(() -> BizException.notFound("订单不存在"));
        requireBuyerExperience(order);
        PaymentRequest payment = payments.lockByPayNo(found.payNo()).orElseThrow();
        requireChannel(payment);
        if (result == null) throw BizException.badRequest("RESULT_REQUIRED", "请选择体验结果");
        PaymentRequest.Status desired = switch (result) {
            case SUCCESS -> PaymentRequest.Status.PAID;
            case CANCEL -> PaymentRequest.Status.CLOSED;
            case FAIL -> PaymentRequest.Status.FAILED;
        };
        if (payment.getStatus() == desired) return view(token, order, payment);
        if (payment.getStatus() != PaymentRequest.Status.CREATED)
            throw BizException.conflict("PAY_STATUS_INVALID", "本次支付已结束，请返回订单查看或重新发起");
        requirePayable(order);
        if (payment.getAmountCents() != order.getTotalCents())
            throw BizException.conflict("PAY_AMOUNT_MISMATCH", "支付单与订单金额不一致，请联系平台");
        payment.setStatus(desired);
        if (result == Result.SUCCESS) {
            Instant now = Instant.now();
            if (!trade.markPaid(order.getId(), now))
                throw BizException.conflict("ORDER_NOT_PAYABLE", "订单状态已变化，请刷新后查看");
            payment.setPaidAt(now);
        }
        payments.saveAndFlush(payment);
        // No ledger_entries, real channel calls, fees, or finance allocation are written here.
        return view(token, order, payment);
    }

    @Transactional(readOnly = true)
    public byte[] qr(String token) {
        Session session = get(token);
        URI origin = URI.create(properties.getFrontendOrigin());
        if (origin.getHost() == null || !("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null)
            throw BizException.conflict("CHECKOUT_ORIGIN_INVALID", "收银页域名配置不正确");
        String url = origin.resolve(session.checkoutPath()).toString();
        try {
            var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320,
                    Map.of(EncodeHintType.MARGIN, 4, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            var image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
            try {
                for (int y = 0; y < 320; y++) for (int x = 0; x < 320; x++)
                    image.setRGB(x, y, matrix.get(x, y) ? 0x18231e : 0xffffff);
                var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
                return output.toByteArray();
            } finally { image.flush(); }
        } catch (Exception ex) { throw BizException.conflict("QR_UNAVAILABLE", "二维码暂时无法生成，请使用下方链接"); }
    }

    private PaymentRequest find(String token) {
        validateToken(token);
        var ids = db.queryForList("SELECT payment_id FROM experience_payment_sessions WHERE token=?", Long.class, token);
        if (ids.isEmpty()) throw BizException.notFound("体验收银页不存在");
        return payments.findById(ids.getFirst()).orElseThrow(() -> BizException.notFound("支付单不存在"));
    }
    private record Pointer(long orderId, String payNo) {}
    private void validateToken(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw BizException.notFound("体验收银页不存在");
    }
    private void requireBuyerExperience(Order order) {
        SecurityUtils.requireOwner(order.getBuyerId());
        if (!order.isInteractiveExperience()) throw BizException.forbidden("该订单不支持体验支付");
    }
    private void requireChannel(PaymentRequest payment) {
        if (payment.getChannel() != PaymentRequest.Channel.EXPERIENCE_QR || !payment.isSimulated())
            throw BizException.forbidden("支付渠道与体验订单不匹配");
    }
    private void requirePayable(Order order) {
        if (order.getFulfillmentStatus() != Order.FulfillmentStatus.PENDING_PAYMENT || order.getPayStatus() != Order.PayStatus.UNPAID)
            throw BizException.conflict("ORDER_NOT_PAYABLE", "订单已结束付款，请返回查看订单状态");
        if (!order.getExpiresAt().isAfter(Instant.now()))
            throw BizException.conflict("ORDER_EXPIRED", "付款时间已结束，请重新下单");
    }
    private Session view(String token, Order order, PaymentRequest payment) {
        String status = payment.getStatus().name();
        if (payment.getStatus() == PaymentRequest.Status.PAID && order.getRefundStatus() == Order.RefundStatus.FULL) status = "REFUNDED";
        else if (payment.getStatus() == PaymentRequest.Status.CLOSED) status = "CANCELLED";
        else if (payment.getStatus() != PaymentRequest.Status.PAID && !order.getExpiresAt().isAfter(Instant.now())) status = "EXPIRED";
        else if (payment.getStatus() != PaymentRequest.Status.PAID && order.getFulfillmentStatus() == Order.FulfillmentStatus.CLOSED) status = "CLOSED";
        var items = db.query("SELECT title,quantity,price_cents FROM order_items WHERE order_id=? ORDER BY id",
                (rs, row) -> new Item(rs.getString("title"), rs.getInt("quantity"), rs.getLong("price_cents")), order.getId());
        return new Session(token, payment.getPayNo(), order.getOrderNo(), payment.getAmountCents(), status,
                order.getRefundStatus().name(), order.getExpiresAt(), "/experience-pay/" + token,
                "/api/v1/experience-pay/" + token + "/qr", items);
    }
}
