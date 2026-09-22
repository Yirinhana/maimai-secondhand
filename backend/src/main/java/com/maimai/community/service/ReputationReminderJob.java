package com.maimai.community.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ReputationReminderJob {
    private final ReputationService service;
    public ReputationReminderJob(ReputationService service){this.service=service;}
    @Scheduled(fixedDelay=60000,initialDelay=30000)
    public void remind(){service.remindCompleted();}
}
