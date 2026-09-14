package com.maimai.support.controller;
import com.maimai.support.dto.SupportDtos.*;
import com.maimai.support.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {
    private final SupportTicketService tickets;
    private final SupportAiService ai;
    public SupportController(SupportTicketService tickets,SupportAiService ai) { this.tickets=tickets; this.ai=ai; }
    @GetMapping("/faq") public List<SupportFaq.Item> faq() { return SupportFaq.items(); }
    @PostMapping("/tickets") public Ticket create(@Valid @RequestBody CreateTicket body) { return tickets.create(body); }
    @GetMapping("/tickets") public Page<Ticket> mine(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) { return tickets.mine(page,size); }
    @GetMapping("/tickets/{id}") public Ticket detail(@PathVariable long id) { return tickets.detail(id); }
    @GetMapping("/tickets/{id}/messages") public Page<Message> messages(@PathVariable long id,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) { return tickets.messages(id,page,size); }
    @PostMapping("/tickets/{id}/messages") public Message reply(@PathVariable long id,@Valid @RequestBody WriteMessage body) { return tickets.reply(id,body); }
    @PostMapping("/tickets/{id}/ai-help") public Message ai(@PathVariable long id,@Valid @RequestBody AiHelp body) { return ai.explain(id,body.topic()); }
    @GetMapping("/admin/tickets") public Page<Ticket> queue(@RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) { return tickets.queue(status,page,size); }
    @PostMapping("/admin/tickets/{id}/claim") public Ticket claim(@PathVariable long id) { return tickets.transition(id,"CLAIM"); }
    @PostMapping("/admin/tickets/{id}/close") public Ticket close(@PathVariable long id) { return tickets.transition(id,"CLOSE"); }
    @PostMapping("/admin/tickets/{id}/reopen") public Ticket reopen(@PathVariable long id) { return tickets.transition(id,"REOPEN"); }
}
