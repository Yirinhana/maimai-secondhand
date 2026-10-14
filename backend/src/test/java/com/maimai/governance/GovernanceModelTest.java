package com.maimai.governance;

import com.maimai.support.ai.HermesTransport;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GovernanceModelTest {
    final JsonMapper json=JsonMapper.builder().build();
    GovernanceSnapshot.Snapshot source(String text){return new GovernanceSnapshot.Snapshot("PRODUCT_COMMENT",7,"PUBLIC","PRODUCT:3","/products/3","商品讨论",1L,2L,text,"没有成交证据");}
    String answer(String evidence){return json.writeValueAsString(new GovernanceModel.Assessment("COMPLAINT","NORMAL","用户反映未收到商品，待核实",evidence,"需订单和沟通记录","请人工核对订单与交付情况"));}
    @Test void parsesOnlyGroundedStructuredAdvice(){
        assertThat(MiniMaxGovernanceModel.parse(answer("一直没有收到货"),"我一直没有收到货，帮我查一下").category()).isEqualTo("COMPLAINT");
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse(answer("已查明卖家欺诈"),"正常留言")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse(answer(""),"正常留言")).isInstanceOf(IllegalArgumentException.class);
        assertThat(MiniMaxGovernanceModel.parse("```json\n"+answer("留言")+"\n```","留言").evidence()).isEqualTo("留言");
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse("说明文字\n```json\n"+answer("留言")+"\n```","留言")).isInstanceOf(RuntimeException.class);
    }
    @Test void refusesUnexpectedFieldsAndUnsupportedActions(){
        var values=new java.util.HashMap<String,String>(Map.of("category","BAN_USER","priority","HIGH","summary","违规","evidence","原文","missing","","suggestion","封号"));
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse(json.writeValueAsString(values),"原文")).isInstanceOf(IllegalArgumentException.class);
        values.put("category","COMPLAINT");values.put("execute","ban");
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse(json.writeValueAsString(values),"原文")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void transportIsDirectOfficialEndpointWithNoToolsOrAccountIds(){
        var wire=mock(HermesTransport.class);
        when(wire.post(any(),anyString(),anyString())).thenReturn(json.writeValueAsString(Map.of("choices",java.util.List.of(Map.of("message",Map.of("content",answer("要求退款")))))));
        var model=new MiniMaxGovernanceModel(true,"https://api.minimaxi.com/v1","test-only-credential","MiniMax-M3",wire);
        assertThat(model.assess(source("用户要求退款")).evidence()).isEqualTo("要求退款");
        verify(wire).post(eq(URI.create("https://api.minimaxi.com/v1/chat/completions")),eq("test-only-credential"),argThat(body->body.contains("\"tool_choice\":\"none\"")&&!body.contains("actorId")&&!body.contains("test-only-credential")));
    }
    @Test void failsClosedOnUnsafeConfigurationAndProviderToolCalls(){
        var wire=mock(HermesTransport.class);
        for(String base:java.util.List.of("http://api.minimaxi.com/v1","https://evil.example/v1","https://api.minimaxi.com/v1?redirect=1","https://api.minimaxi.com:9999/v1")){
            var model=new MiniMaxGovernanceModel(true,base,"test-credential","model",wire);
            assertThat(model.configured()).isFalse();assertThatThrownBy(()->model.assess(source("投诉"))).isInstanceOf(RuntimeException.class);
        }
        verifyNoInteractions(wire);
        when(wire.post(any(),anyString(),anyString())).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{}\",\"tool_calls\":[{}]}}]}");
        assertThatThrownBy(()->new MiniMaxGovernanceModel(true,"https://api.minimax.io/v1","test-credential","model",wire).assess(source("投诉"))).hasMessageNotContaining("test-credential");
    }
    @Test void redactsUserSecretsAndContactBeforeAnalysis(){
        String value=GovernanceSnapshot.redact("邮箱 a@example.test 电话13800138000 密码：secret-password 微信：wx-private\n https://example.test?token=private sk_1234567890123456");
        assertThat(value).doesNotContain("a@example.test","13800138000","secret-password","wx-private","token=private","sk_1234567890123456");
    }
    @Test void cheapFilterSavesCallsButDoesNotPretendToJudge(){
        assertThat(GovernanceTriage.candidate(source("你好，请问尺寸是多少？"))).isFalse();
        assertThat(GovernanceTriage.candidate(source("已经付款一直没收到，卖家也失联了"))).isTrue();
        assertThat(GovernanceTriage.candidate(source("请不要私下转账，这是安全提醒"))).isTrue();
        var normal=source("商品看起来不错");
        assertThat(GovernanceTriage.candidate(new GovernanceSnapshot.Snapshot("REPORT",1,"PUBLIC","x","/x","举报",1L,2L,normal.text(),""))).isTrue();
    }
    @Test void repairsEscapedLineBreaksButNeverAcceptsInventedEvidence(){
        String source="商品破损\n希望处理退款";
        assertThat(MiniMaxGovernanceModel.parse(answer("商品破损\\n希望处理退款"),source).evidence()).isEqualTo(source);
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse(answer("商品破损\\n卖家已承认诈骗"),source)).isInstanceOf(IllegalArgumentException.class);
        String normal=json.writeValueAsString(new GovernanceModel.Assessment("NONE","LOW","维护测试，暂无真实投诉","不属于原文的无关引用","","无需处罚，请保留人工核查入口"));
        assertThat(MiniMaxGovernanceModel.parse(normal,source).evidence()).isEmpty();
    }
    @Test void wrappedProviderResultStillEnforcesSchemaAndDoesNotLeakMarkdown(){
        var wire=mock(HermesTransport.class);
        when(wire.post(any(),anyString(),anyString())).thenReturn(json.writeValueAsString(Map.of("choices",java.util.List.of(Map.of("message",Map.of("content","```json\n"+answer("申请退款")+"\n```","tool_calls",java.util.List.of()))))));
        var model=new MiniMaxGovernanceModel(true,"https://api.minimaxi.com/v1","test-credential","model",wire);
        assertThat(model.assess(source("申请退款")).evidence()).isEqualTo("申请退款");
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse("```json\n{\"execute\":\"ban\"}\n```","申请退款")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->MiniMaxGovernanceModel.parse("x".repeat(8193),"申请退款")).isInstanceOf(IllegalArgumentException.class);
    }
}
