package com.maimai.support.chat;

import com.maimai.common.BizException;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import com.maimai.support.ai.SupportAiGateway;
import org.springframework.stereotype.Service;
import java.util.List;

/** Model requests run outside transactions. Store validates ownership again on completion. */
@Service
public class TinaChatService {
    private final TinaChatStore store;
    private final SupportAiGateway gateway;
    private final SimpleRateLimiter limiter;
    private final SupportNavigation navigation;
    public TinaChatService(TinaChatStore store,SupportAiGateway gateway,SimpleRateLimiter limiter,SupportNavigation navigation) {
        this.store=store;this.gateway=gateway;this.limiter=limiter;this.navigation=navigation;
    }
    public record Assistant(String name,boolean enabled,int maxMessageChars,String notice,boolean modelEnabled) { }
    public Assistant assistant() { return new Assistant("麦仔",true,1000,"商品与规则查询不消耗模型额度；AI 建议仅供参考，交易操作由你确认。",gateway.configured()); }
    public List<TinaChatStore.Turn> history() { return store.history(); }
    public TinaChatStore.Turn send(String requestId,String question) {
        long owner=SecurityUtils.currentUserId();
        limiter.require("tina:chat:"+owner,8,300,"提问有点频繁，请稍后重试");
        limiter.require("tina:global",30,60,"麦仔正在忙碌，请稍后重试");
        var attempt=store.begin(requestId,question);
        if(!attempt.invoke()) return attempt.turn();
        String answer;
        try {
            var direct=navigation.resolve(question);
            if(direct.isPresent()) return store.finishNavigation(attempt,direct.get());
            if(!gateway.configured()) return store.finishNavigation(attempt,new SupportNavigation.Reply(
                    "自由问答暂未接通，但我仍能查找站内商品、打开用户协议和说明售后入口。可以试试“推荐外套”或“查看用户协议”，也可联系人工。",java.util.List.of(
                    new SupportNavigation.Resource("PAGE","帮助与人工工单","/support",null,null,"提交问题并跟进处理",false))));
            answer=gateway.chat(store.context(attempt.turn().id()));
        }
        catch(BizException error) { return store.finish(attempt,null,error.getCode()); }
        catch(RuntimeException error) { return store.finish(attempt,null,"AI_UNAVAILABLE"); }
        return store.finish(attempt,answer,null);
    }
    public void clear() { store.clear(); }
}
