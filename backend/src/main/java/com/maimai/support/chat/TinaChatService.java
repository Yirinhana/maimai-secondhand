package com.maimai.support.chat;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import com.maimai.support.ai.SupportAiGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

/** Model requests run outside transactions. Store validates ownership again on completion. */
@Service
public class TinaChatService {
    private final TinaChatStore store;
    private final SupportAiGateway gateway;
    private final SimpleRateLimiter limiter;
    public TinaChatService(TinaChatStore store,SupportAiGateway gateway,SimpleRateLimiter limiter) {
        this.store=store;this.gateway=gateway;this.limiter=limiter;
    }
    public record Assistant(String name,boolean enabled,int maxMessageChars,String notice) { }
    public Assistant assistant() { return new Assistant("麦仔",gateway.configured(),1000,"AI 回复仅供参考，订单处理以平台流程和人工结果为准。"); }
    public List<TinaChatStore.Turn> history() { return store.history(); }
    public TinaChatStore.Turn send(String requestId,String question) {
        long owner=SecurityUtils.currentUserId();
        if(!gateway.configured()) throw new BizException("AI_NOT_CONFIGURED","麦仔暂未接通，请查看常见问题或联系人工",HttpStatus.SERVICE_UNAVAILABLE);
        limiter.require("tina:chat:"+owner,8,300,"提问有点频繁，请稍后重试");
        limiter.require("tina:global",30,60,"麦仔正在忙碌，请稍后重试");
        var attempt=store.begin(requestId,question);
        if(!attempt.invoke()) return attempt.turn();
        String answer;
        try { answer=gateway.chat(store.context(attempt.turn().id())); }
        catch(BizException error) { return store.finish(attempt,null,error.getCode()); }
        catch(RuntimeException error) { return store.finish(attempt,null,"AI_UNAVAILABLE"); }
        return store.finish(attempt,answer,null);
    }
    public void clear() { store.clear(); }
}
