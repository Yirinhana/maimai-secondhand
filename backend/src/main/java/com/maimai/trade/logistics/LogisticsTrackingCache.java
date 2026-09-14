package com.maimai.trade.logistics;

import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/** 每个运单持久化退避30分钟；短事务抢占后才请求供应商，不占数据库锁等待网络。 */
@Service
public class LogisticsTrackingCache {
    private static final Duration POLL_INTERVAL = Duration.ofMinutes(30);
    private static final Map<String, String> CARRIERS = Map.ofEntries(
            Map.entry("顺丰", "shunfeng"), Map.entry("顺丰速运", "shunfeng"),
            Map.entry("中通", "zhongtong"), Map.entry("中通快递", "zhongtong"),
            Map.entry("圆通", "yuantong"), Map.entry("圆通速递", "yuantong"),
            Map.entry("申通", "shentong"), Map.entry("韵达", "yunda"),
            Map.entry("京东", "jd"), Map.entry("邮政", "youzhengguonei"));
    private final JdbcTemplate jdbc;
    private final LogisticsService provider;
    private final TransactionTemplate transaction;

    public LogisticsTrackingCache(JdbcTemplate jdbc, LogisticsService provider, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static String normalizeCarrier(String value) {
        String carrier = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        carrier = CARRIERS.getOrDefault(carrier, carrier);
        if (!carrier.matches("[a-z0-9_]{2,40}")) {
            throw BizException.badRequest("LOGISTICS_INVALID_CARRIER", "请选择承运商或填写其快递100公司代码");
        }
        return carrier;
    }

    public static String validateNumber(String value) {
        if (value == null || !value.matches("[A-Za-z0-9\\-]{6,32}")) {
            throw BizException.badRequest("LOGISTICS_INVALID_NUMBER", "运单号须为6至32位字母、数字或连字符");
        }
        return value;
    }

    public Snapshot query(String carrierName, String trackingNo, String phone) {
        String carrier = normalizeCarrier(carrierName);
        String number = validateNumber(trackingNo);
        String fingerprint = fingerprint(phone);
        Instant now = Instant.now();
        boolean acquired = Boolean.TRUE.equals(transaction.execute(ignored -> {
            // INSERT IGNORE在已有行上可能先拿共享锁，随后并发升级为写锁会死锁。
            // upsert直接取得唯一运单的排他锁，使本次抢占串行完成。
            jdbc.update("INSERT INTO logistics_query_cache(carrier,tracking_no) VALUES (?,?) ON DUPLICATE KEY UPDATE carrier=?", carrier, number, carrier);
            Snapshot before = snapshot(carrier, number, true);
            if (before.attemptedAt() != null && now.isBefore(before.attemptedAt().plus(POLL_INTERVAL))) return false;
            if (!fingerprint.equals(before.phoneFingerprint())) {
                jdbc.update("UPDATE logistics_query_cache SET fetched_at=NULL,trace_status=NULL,traces=NULL,phone_fingerprint=? WHERE carrier=? AND tracking_no=?",
                        fingerprint, carrier, number);
            }
            jdbc.update("UPDATE logistics_query_cache SET last_attempt_at=?,error_code='QUERY_PENDING' WHERE carrier=? AND tracking_no=?",
                    Timestamp.from(now), carrier, number);
            return true;
        }));
        if (acquired) {
            try {
                LogisticsTrace trace = provider.queryTrace(carrier, number, phone);
                transaction.executeWithoutResult(ignored -> jdbc.update("""
                        UPDATE logistics_query_cache SET fetched_at=?,trace_status=?,traces=?,error_code=NULL
                        WHERE carrier=? AND tracking_no=?
                        """, Timestamp.from(trace.updatedAt()), trace.status(), trace.tracesText(), carrier, number));
            } catch (RuntimeException error) {
                String code = error instanceof BizException biz && biz.getCode().matches("[A-Z_]{1,64}")
                        ? biz.getCode() : "LOGISTICS_UNAVAILABLE";
                transaction.executeWithoutResult(ignored -> jdbc.update(
                        "UPDATE logistics_query_cache SET error_code=? WHERE carrier=? AND tracking_no=?", code, carrier, number));
            }
        }
        Snapshot result = snapshot(carrier, number, false);
        if (!fingerprint.equals(result.phoneFingerprint())) {
            return new Snapshot(null, null, null, null, "LOGISTICS_ORDER_PHONE_MISMATCH", null);
        }
        return result;
    }

    private static String fingerprint(String phone) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    (phone == null ? "" : phone.strip()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }

    private Snapshot snapshot(String carrier, String number, boolean lock) {
        return jdbc.queryForObject("SELECT * FROM logistics_query_cache WHERE carrier=? AND tracking_no=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> {
                    Timestamp fetched = rs.getTimestamp("fetched_at");
                    Timestamp attempted = rs.getTimestamp("last_attempt_at");
                    return new Snapshot(rs.getString("trace_status"), rs.getString("traces"),
                            fetched == null ? null : fetched.toInstant(), attempted == null ? null : attempted.toInstant(), rs.getString("error_code"),
                            rs.getString("phone_fingerprint"));
                }, carrier, number);
    }

    public record Snapshot(String status, String traces, Instant fetchedAt, Instant attemptedAt, String errorCode, String phoneFingerprint) {}
}
