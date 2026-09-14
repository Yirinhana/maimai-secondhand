package com.maimai.identity.service;

import com.maimai.identity.domain.EmailVerification.Purpose;
import com.maimai.common.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Uses the caller transaction: account changes and successful consumption commit atomically. */
@Service
public class EmailCodeVerifier {
    private final JdbcTemplate jdbc;
    public EmailCodeVerifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public boolean consume(String email, Purpose purpose, String code) {
        var records = jdbc.query("""
                SELECT id, code_hash, expires_at, attempts, consumed_at FROM email_verifications
                WHERE email=? AND purpose=? ORDER BY created_at DESC, id DESC LIMIT 1 FOR UPDATE
                """, (rs, row) -> new Code(rs.getLong("id"), rs.getString("code_hash"),
                rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempts"),
                rs.getTimestamp("consumed_at") != null), email, purpose.name());
        if (records.isEmpty()) return false;
        var record = records.getFirst();
        if (record.used() || !record.expiresAt().isAfter(Instant.now()) || record.attempts() >= 5) return false;
        if (!MessageDigest.isEqual(record.hash().getBytes(StandardCharsets.UTF_8),
                hash(code).getBytes(StandardCharsets.UTF_8))) {
            jdbc.update("UPDATE email_verifications SET attempts=attempts+1 WHERE id=?", record.id());
            return false;
        }
        jdbc.update("UPDATE email_verifications SET consumed_at=CURRENT_TIMESTAMP(6) WHERE id=?", record.id());
        return true;
    }

    public static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    private record Code(long id, String hash, Instant expiresAt, int attempts, boolean used) {}

    /** Only this pre-write rejection commits the attempt counter; other failures still roll back. */
    public static final class InvalidCodeException extends BizException {
        public InvalidCodeException() {
            super("CODE_INVALID", "验证码不正确或已失效", HttpStatus.BAD_REQUEST);
        }
    }
}
