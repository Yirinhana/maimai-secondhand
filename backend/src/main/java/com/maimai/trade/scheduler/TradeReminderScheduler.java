package com.maimai.trade.scheduler;

import com.maimai.trade.service.TradeReminderService;
import com.maimai.payment.finance.FinanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("!test")
public class TradeReminderScheduler {
    private static final Logger log=LoggerFactory.getLogger(TradeReminderScheduler.class);
    private final TradeReminderService reminders;
    private final FinanceService finance;
    private final JdbcTemplate jdbc;
    public TradeReminderScheduler(TradeReminderService reminders,FinanceService finance,JdbcTemplate jdbc) {
        this.reminders=reminders;this.finance=finance;this.jdbc=jdbc;
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void scan() {
        for(Long id:reminders.candidates()) {
            try {reminders.check(id);}catch(RuntimeException error){log.warn("Trade reminder failed, orderId={}",id);}
        }
        var missing=jdbc.queryForList("SELECT o.id FROM orders o WHERE o.pay_status='PAID' AND NOT EXISTS(SELECT 1 FROM finance_allocation_expectations a WHERE a.order_id=o.id) ORDER BY o.id LIMIT 100",Long.class);
        for(Long id:missing) {
            try {finance.captureExpectedAllocation(id);}catch(RuntimeException error){log.warn("Expected allocation capture failed, orderId={}",id);}
        }
    }
}
