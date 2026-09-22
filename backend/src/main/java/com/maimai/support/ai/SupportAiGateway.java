package com.maimai.support.ai;
import com.maimai.support.service.SupportFaq;
/** FAQ explanation and explicitly submitted support chat; no tools or private record retrieval. */
public interface SupportAiGateway {
    String explain(SupportFaq.Topic topic);
    record ChatMessage(String role, String content) { }
    default boolean configured() { return false; }
    default String modelName() { return "unconfigured"; }
    default String advise(String instructions, String context) {
        throw new com.maimai.common.BizException("AI_NOT_CONFIGURED", "麦仔暂未接通，请稍后重试或转人工", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }
    default String chat(java.util.List<ChatMessage> history) {
        throw new com.maimai.common.BizException("AI_NOT_CONFIGURED", "麦仔暂未接通，请查看常见问题或联系人工", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
    }
}
