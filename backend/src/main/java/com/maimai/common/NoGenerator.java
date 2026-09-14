package com.maimai.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** 业务单号生成：前缀 + UTC时间戳 + 随机段，配合数据库唯一约束兜底。 */
public final class NoGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private NoGenerator() {
    }

    public static String next(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        sb.append(LocalDateTime.now(ZoneOffset.UTC).format(FMT));
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
