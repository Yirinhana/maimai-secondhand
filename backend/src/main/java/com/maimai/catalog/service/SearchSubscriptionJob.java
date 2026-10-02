package com.maimai.catalog.service;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@Profile("!test")
public class SearchSubscriptionJob {
    private final SearchSubscriptionService service;
    public SearchSubscriptionJob(SearchSubscriptionService service){this.service=service;}
    @Scheduled(fixedDelay=300000,initialDelay=120000)
    public void check(){for(long id:service.due())service.check(id);}
}
