package com.maimai.support.ai;

import com.maimai.common.BizException;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class HermesHttpTransportTest {
    private final URI endpoint=URI.create("https://hermes.example.test/v1/chat/completions");
    @Test
    void timeoutCancelsIncompleteResponseAndReturnsGatewayTimeout() {
        var pending=new CompletableFuture<HermesHttpTransport.Response>();
        var transport=new HermesHttpTransport(request->pending,Duration.ofMillis(30));
        assertThatThrownBy(()->transport.post(endpoint,"test-token","{}"))
                .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("AI_TIMEOUT"));
        assertThat(pending.isCancelled()).isTrue();
    }
    @Test
    void headersAndDeadlineAreSetAndProviderFailuresAreSanitized() {
        var transport=new HermesHttpTransport(request->{
            assertThat(request.timeout()).contains(Duration.ofSeconds(15));
            assertThat(request.headers().firstValue("Authorization")).contains("Bearer test-token");
            return CompletableFuture.completedFuture(new HermesHttpTransport.Response(302,"secret redirect".getBytes()));
        },Duration.ofSeconds(15));
        assertThatThrownBy(()->transport.post(endpoint,"test-token","{}"))
                .isInstanceOfSatisfying(BizException.class,e->{assertThat(e.getCode()).isEqualTo("AI_UNAVAILABLE");assertThat(e.getMessage()).doesNotContain("secret","test-token");});
    }
    @Test
    void oversizedStreamingBodyCancelsUpstreamInsteadOfAllocatingUnboundedMemory() {
        var cancelled=new AtomicBoolean();var subscriber=new HermesHttpTransport.LimitedBody();
        subscriber.onSubscribe(new Flow.Subscription(){ public void request(long n){} public void cancel(){cancelled.set(true);} });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65_537])));
        assertThat(cancelled).isTrue();
        assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
    }
}
