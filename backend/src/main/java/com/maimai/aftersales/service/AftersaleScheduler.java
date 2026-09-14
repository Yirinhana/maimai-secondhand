package com.maimai.aftersales.service;

import com.maimai.aftersales.domain.Aftersale;
import com.maimai.aftersales.repo.AftersaleQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.stream.Stream;

/** Scan IDs only; each transition locks and rechecks its own transaction. */
@Component
@org.springframework.context.annotation.Profile("!test")
public class AftersaleScheduler {
    private static final Logger log = LoggerFactory.getLogger(AftersaleScheduler.class);
    private final AftersaleQueryRepository repository;
    private final AftersaleService service;
    public AftersaleScheduler(AftersaleQueryRepository repository, AftersaleService service) {
        this.repository = repository;
        this.service = service;
    }
    @Scheduled(fixedDelay = 60000)
    public void escalateExpired() {
        Instant now=Instant.now();
        var ids=Stream.of(
            repository.findByStatusAndSellerDeadlineBefore(Aftersale.Status.PENDING_SELLER, now),
            repository.findByStatusAndReturnDeadlineBefore(Aftersale.Status.PENDING_RETURN, now),
            repository.findByStatusAndReturnInspectionDeadlineBefore(Aftersale.Status.RETURN_SHIPPED, now),
            repository.findActiveWithUnavailableSeller())
            .flatMap(java.util.Collection::stream)
            .map(Aftersale::getId).distinct().toList();
        for (Long id : ids) {
            try { service.escalateExpired(id); }
            catch (RuntimeException error) { log.warn("Aftersale escalation failed, id={}", id); }
        }
    }
}
