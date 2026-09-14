package com.maimai.community.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
class FootprintRetentionJob {
    private final CollectionService collections;
    FootprintRetentionJob(CollectionService collections) { this.collections = collections; }
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void purgeBatch() { collections.cleanupExpiredFootprints(); }
}
