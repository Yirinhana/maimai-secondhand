package com.maimai.support;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.support.ai.SupportAiGateway;
import com.maimai.support.chat.*;
import com.maimai.support.service.SupportFaq;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TinaChatTest {
    @Autowired TinaChatStore store;
    @Autowired JdbcTemplate db;
    long owner,other;
    @BeforeEach void setup() { owner=user();other=user();login(owner); }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        db.update("DELETE FROM support_ai_turns WHERE owner_id IN (?,?)",owner,other);
        db.update("DELETE FROM users WHERE id IN (?,?)",owner,other);
    }
    @Test void persistentHistoryAndContextAreOwnerScoped() {
        var first=store.begin(key(),"服务费是多少？");store.finish(first,"成交商品金额的0.03%。",null);
        var second=store.begin(key(),"运费也算吗？");
        assertThat(store.context(second.turn().id())).extracting(SupportAiGateway.ChatMessage::role).containsExactly("user","assistant","user");
        assertThat(store.history()).hasSize(2);
        login(other);
        assertThat(store.history()).isEmpty();code("NOT_FOUND",()->store.context(second.turn().id()));
        code("AI_CHAT_CHANGED",()->store.finish(second,"不允许越权写入",null));
        store.clear();login(owner);assertThat(store.history()).hasSize(2);
    }
    @Test void requestIdReplaysWithoutDuplicateCallAndCannotChangeQuestion() {
        var request=key();var attempt=store.begin(request,"问题一");
        assertThat(store.begin(request,"问题一").invoke()).isFalse();
        store.finish(attempt,"回复一",null);
        assertThat(store.begin(request,"问题一").turn().answer()).isEqualTo("回复一");
        assertThat(store.begin(request,"问题一").invoke()).isFalse();
        code("AI_REQUEST_REUSED",()->store.begin(request,"换了一个问题"));
        assertThat(store.history()).hasSize(1);
    }
    @Test void failedTurnRetriesAndOldAttemptCannotOverwriteNewAttempt() {
        var request=key();var first=store.begin(request,"退款说明");
        store.finish(first,null,"AI_TIMEOUT");var retry=store.begin(request,"退款说明");
        assertThat(retry.invoke()).isTrue();assertThat(retry.turn().id()).isEqualTo(first.turn().id());
        code("AI_CHAT_CHANGED",()->store.finish(first,"过期结果",null));
        store.finish(retry,"新的正确回复",null);
        assertThat(store.history().getFirst().answer()).isEqualTo("新的正确回复");
    }
    @Test void interruptedWorkExpiresAndDoesNotBlockFutureQuestions() {
        var first=store.begin(key(),"旧请求");code("AI_CHAT_BUSY",()->store.begin(key(),"并发请求"));
        db.update("UPDATE support_ai_turns SET updated_at=DATE_SUB(NOW(6),INTERVAL 3 MINUTE) WHERE id=?",first.turn().id());
        assertThat(store.history().getFirst().status()).isEqualTo("FAILED");
        assertThat(store.begin(key(),"新请求").invoke()).isTrue();
    }
    @Test void clearingDuringNetworkWaitPreventsMessageResurrection() {
        var pending=store.begin(key(),"不应复活的正文");store.clear();
        code("AI_CHAT_CHANGED",()->store.finish(pending,"迟到的回复",null));assertThat(store.history()).isEmpty();
    }
    @Test void modelWaitHasNoTransactionAndUnavailableModelIsNotFaked() {
        var calls=new AtomicInteger();
        SupportAiGateway gateway=new SupportAiGateway() {
            public String explain(SupportFaq.Topic topic) { throw new AssertionError(); }
            public boolean configured() { return true; }
            public String chat(List<ChatMessage> history) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                calls.incrementAndGet();return "平台费不含运费。";
            }
        };
        var service=new TinaChatService(store,gateway,new SimpleRateLimiter());var request=key();
        assertThat(service.send(request,"费用规则").answer()).isEqualTo("平台费不含运费。");
        service.send(request,"费用规则");assertThat(calls).hasValue(1);
        SupportAiGateway offline=topic->{throw new AssertionError("must not call offline gateway");};
        code("AI_NOT_CONFIGURED",()->new TinaChatService(store,offline,new SimpleRateLimiter()).send(key(),"新的问题"));
        assertThat(store.history()).hasSize(1);
    }
    @Test void sameRequestConcurrentCallsReserveOnlyOneTurn() throws Exception {
        String request=key();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> call=()->{login(owner);try{return store.begin(request,"并发问题").invoke();}finally{SecurityContextHolder.clearContext();}};
            var futures=pool.invokeAll(List.of(call,call));
            assertThat(List.of(futures.get(0).get(),futures.get(1).get())).containsExactlyInAnyOrder(true,false);
        }
        assertThat(store.history()).hasSize(1);
    }
    @Test void disabledAccountCannotReadOrCompleteChat() {
        var pending=store.begin(key(),"停用前提问");db.update("UPDATE users SET status='DISABLED' WHERE id=?",owner);
        code("UNAUTHORIZED",()->store.history());code("UNAUTHORIZED",()->store.finish(pending,"不能落库",null));
    }
    private long user() {
        String email="tina-"+UUID.randomUUID()+"@example.invalid";
        db.update("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test','Tina test','ACTIVE')",email);
        return db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    private static String key() { return UUID.randomUUID().toString(); }
    private static void code(String expected,Runnable action) { assertThatThrownBy(action::run).isInstanceOfSatisfying(BizException.class,error->assertThat(error.getCode()).isEqualTo(expected)); }
    private static void login(long user) {
        var principal=new AuthenticatedUser(user,"tina@example.invalid","Tina test",Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
