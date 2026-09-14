package com.maimai.trade.logistics;

import com.maimai.common.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LogisticsTrackingCacheTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    final String number = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

    @AfterEach void cleanupOnlyFixture() {
        jdbc.update("DELETE FROM logistics_query_cache WHERE carrier='yuantong' AND tracking_no=?", number);
    }

    @Test void simultaneousQueriesCallProviderOnlyOnceAndNewInstanceUsesPersistentCache() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimai_test");
        var calls = new AtomicInteger();
        LogisticsService provider = (carrier, tracking, phone) -> {
            calls.incrementAndGet();
            return new LogisticsTrace("IN_TRANSIT", "本地测试轨迹", Instant.now());
        };
        var cache = new LogisticsTrackingCache(jdbc, provider, transactions);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<LogisticsTrackingCache.Snapshot>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) results.add(pool.submit(() -> {
                start.await(); return cache.query("圆通", number, "test-phone");
            }));
            start.countDown();
            for (var result : results) result.get(30, TimeUnit.SECONDS);
        }
        var afterRestart = new LogisticsTrackingCache(jdbc, provider, transactions).query("yuantong", number, "test-phone");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(afterRestart.traces()).isEqualTo("本地测试轨迹");
        assertThat(afterRestart.errorCode()).isNull();
    }

    @Test void failuresRetainPriorSnapshotAndAlsoBackOffForThirtyMinutes() {
        var calls = new AtomicInteger();
        LogisticsService provider = (carrier, tracking, phone) -> {
            if (calls.incrementAndGet() == 1) return new LogisticsTrace("EXCEPTION", "测试：运输异常", Instant.now());
            throw new BizException("LOGISTICS_UNAVAILABLE", "不可用", HttpStatus.SERVICE_UNAVAILABLE);
        };
        var cache = new LogisticsTrackingCache(jdbc, provider, transactions);
        cache.query("yuantong", number, "same-phone");
        jdbc.update("UPDATE logistics_query_cache SET last_attempt_at=TIMESTAMPADD(MINUTE,-31,CURRENT_TIMESTAMP(6)) WHERE carrier='yuantong' AND tracking_no=?", number);
        var failure = cache.query("yuantong", number, "same-phone");
        assertThat(failure.status()).isEqualTo("EXCEPTION");
        assertThat(failure.traces()).isEqualTo("测试：运输异常");
        assertThat(failure.errorCode()).isEqualTo("LOGISTICS_UNAVAILABLE");
        cache.query("yuantong", number, "same-phone");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void anotherOrdersPhoneCannotReceiveCachedPrivateTrace() {
        var calls = new AtomicInteger();
        var cache = new LogisticsTrackingCache(jdbc, (carrier, tracking, phone) -> {
            calls.incrementAndGet();
            return new LogisticsTrace("DELIVERED", "测试：收件人轨迹", Instant.now());
        }, transactions);
        cache.query("yuantong", number, "phone-A");
        var otherOrder = cache.query("yuantong", number, "phone-B");
        assertThat(otherOrder.traces()).isNull();
        assertThat(otherOrder.fetchedAt()).isNull();
        assertThat(otherOrder.errorCode()).isEqualTo("LOGISTICS_ORDER_PHONE_MISMATCH");
        assertThat(calls.get()).isEqualTo(1);
    }
}
