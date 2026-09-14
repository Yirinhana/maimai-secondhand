package com.maimai.support.ai;

import com.maimai.common.BizException;
import com.maimai.support.service.SupportFaq;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HermesAiGatewayTest {
    @Test
    void missingCredentialsOrUnverifiedHermesCannotMakeNetworkCall() {
        var calls=new AtomicInteger();
        HermesTransport transport=(uri,token,body)->{calls.incrementAndGet();return "{}";};
        for(var gateway:new HermesAiGateway[]{new HermesAiGateway("","","",false,transport),
                new HermesAiGateway("http://127.0.0.1:8642","test","hermes-agent",false,transport)}) {
            assertThatThrownBy(()->gateway.explain(SupportFaq.Topic.FEES)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("AI_NOT_CONFIGURED"));
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void endpointRejectsCredentialsQueryAndArbitraryPathsBeforeCalling() {
        HermesTransport transport=(u,t,b)->{throw new AssertionError("unexpected transport call");};
        for(String base:new String[]{"https://user:pass@example.test", "https://example.test?url=private", "https://example.test/admin", "http://example.test", "file:///etc/passwd"}) {
            assertThatThrownBy(()->new HermesAiGateway(base,"test","hermes-agent",true,transport).explain(SupportFaq.Topic.FEES))
                    .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("AI_NOT_CONFIGURED"));
        }
    }

    @Test
    void onlyFixedFaqMessagesAndNoToolsOrTicketDataAreSent() {
        var payload=new AtomicReference<String>();
        var gateway=new HermesAiGateway("http://127.0.0.1:8642/v1","secret-test-token","hermes-agent",true,(uri,token,body)->{
            assertThat(uri.toString()).isEqualTo("http://127.0.0.1:8642/v1/chat/completions");
            payload.set(body);return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"平台费不含运费。\"}}]}";
        });
        assertThat(gateway.explain(SupportFaq.Topic.FEES)).isEqualTo("平台费不含运费。");
        var root=JsonMapper.builder().build().readTree(payload.get());
        assertThat(root.path("messages").size()).isEqualTo(2);
        assertThat(root.path("tool_choice").asString()).isEqualTo("none");
        assertThat(root.has("tools")).isFalse(); assertThat(root.has("conversation")).isFalse();
        assertThat(payload.get()).doesNotContain("secret-test-token","orderNo","ticketId","email","phone");
    }

    @Test
    void invalidOrToolCallingResponseIsRejected() {
        for(String response:new String[]{"not json","{}","{\"choices\":[]}",
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"退款\",\"tool_calls\":[{}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"退款\",\"function_call\":{}}}]}"}) {
            var gateway=new HermesAiGateway("https://hermes.example.test","test","hermes-agent",true,(u,t,b)->response);
            assertThatThrownBy(()->gateway.explain(SupportFaq.Topic.PAYMENT)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("AI_RESPONSE_INVALID"));
        }
    }

    @Test
    void modelConcurrencyIsOneAndSlotIsReleasedAfterFailure() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var gateway=new HermesAiGateway("https://hermes.example.test","test","hermes-agent",true,(u,t,b)->{
            entered.countDown(); try { if(!release.await(3,TimeUnit.SECONDS)) throw new AssertionError("timeout"); }
            catch(InterruptedException ex){Thread.currentThread().interrupt();}
            return "invalid";
        });
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->assertThatThrownBy(()->gateway.explain(SupportFaq.Topic.FEES)).isInstanceOf(BizException.class));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->gateway.explain(SupportFaq.Topic.FEES)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("RATE_LIMITED"));
            release.countDown();first.get(3,TimeUnit.SECONDS);
            assertThatThrownBy(()->gateway.explain(SupportFaq.Topic.FEES)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getCode()).isEqualTo("AI_RESPONSE_INVALID"));
        }
    }
}
