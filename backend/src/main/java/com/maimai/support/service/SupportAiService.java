package com.maimai.support.service;
import com.maimai.support.ai.SupportAiGateway;
import com.maimai.support.dto.SupportDtos.Message;
import org.springframework.stereotype.Service;

/** The network wait is outside any database transaction, and ownership/state are rechecked on save. */
@Service
public class SupportAiService {
    private final SupportTicketService tickets;
    private final SupportAiGateway ai;
    public SupportAiService(SupportTicketService tickets,SupportAiGateway ai) { this.tickets=tickets; this.ai=ai; }
    public Message explain(long id,SupportFaq.Topic topic) {
        tickets.authorizeAi(id);
        return tickets.saveAi(id,ai.explain(topic));
    }
}
