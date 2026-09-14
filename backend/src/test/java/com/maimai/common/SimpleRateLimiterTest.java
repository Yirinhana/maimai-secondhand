package com.maimai.common;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
class SimpleRateLimiterTest {
    @Test void capsTrackedKeysAndReclaimsExpiredWindows() {
        var clock=new MutableClock(); var limiter=new SimpleRateLimiter(clock,2);
        assertThat(limiter.tryAcquire("a",1,5)).isTrue();
        assertThat(limiter.tryAcquire("b",1,5)).isTrue();
        assertThat(limiter.tryAcquire("c",1,5)).isFalse();
        assertThat(limiter.tryAcquire("a",1,5)).isFalse();
        clock.time+=5000;
        assertThat(limiter.tryAcquire("c",1,5)).isTrue();
        assertThat(limiter.tryAcquire("a",1,5)).isTrue();
    }
    @Test void concurrentRequestsDoNotExceedWindowLimit() throws Exception {
        var limiter=new SimpleRateLimiter();
        try(var executor=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<100;i++)tasks.add(()->limiter.tryAcquire("same",7,60));
            int accepted=0;for(var future:executor.invokeAll(tasks))if(future.get())accepted++;
            assertThat(accepted).isEqualTo(7);
        }
    }
    static class MutableClock extends Clock {
        long time=1_000_000;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(time);}
    }
}
