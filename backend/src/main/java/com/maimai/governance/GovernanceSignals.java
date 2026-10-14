package com.maimai.governance;

import com.maimai.community.repository.CommunityRepository;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Written in the caller's business transaction; no model or network work here. */
@Component
public class GovernanceSignals {
    public static final Set<String> TYPES = Set.of("PRODUCT", "PRODUCT_COMMENT", "ORDER_REVIEW", "DEMAND_POST",
            "DEMAND_REPLY", "REPORT", "AFTERSALE", "SUPPORT_TICKET");
    private final CommunityRepository repo;
    public GovernanceSignals(CommunityRepository repo) { this.repo = repo; }
    public void touch(String type, long id) {
        if (!TYPES.contains(type) || id < 1) throw new IllegalArgumentException("Unknown governance source");
        repo.update("""
            INSERT INTO ai_governance_signals(source_type,source_id) VALUES (?,?)
            ON DUPLICATE KEY UPDATE revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            """, type, id);
    }
}
