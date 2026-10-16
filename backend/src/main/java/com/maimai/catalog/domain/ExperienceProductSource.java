package com.maimai.catalog.domain;

import java.util.Set;

/** Exact provenance allowlist for the no-money checkout; unrelated imported data stays read only. */
public final class ExperienceProductSource {
    public static final String SELLER_SIMULATION = "maimai-seller-simulation-v1";
    private static final Set<String> INTERACTIVE = Set.of(
            "maimai-experience-045", "maimai-experience-catalog-021", SELLER_SIMULATION);

    private ExperienceProductSource() {}

    public static boolean supportsCheckout(String source) {
        return source != null && INTERACTIVE.contains(source);
    }
}
