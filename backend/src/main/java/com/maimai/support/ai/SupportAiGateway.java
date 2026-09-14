package com.maimai.support.ai;
import com.maimai.support.service.SupportFaq;
/** The only model input is a bounded public FAQ topic, never a user-supplied prompt or URL. */
public interface SupportAiGateway {
    String explain(SupportFaq.Topic topic);
}
