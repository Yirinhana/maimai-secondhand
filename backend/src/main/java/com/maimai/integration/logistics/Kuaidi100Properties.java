package com.maimai.integration.logistics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Kuaidi100 (快递100) logistics integration.
 *
 * <p>Credentials are sourced exclusively from environment variables to avoid leaking
 * secrets into source control:
 * <ul>
 *     <li>{@code MAIMAI_KUAIDI100_CUSTOMER} — the customer identifier issued by Kuaidi100</li>
 *     <li>{@code MAIMAI_KUAIDI100_KEY} — the signing key issued by Kuaidi100</li>
 * </ul>
 *
 * <p>Both default to an empty string so that {@link Kuaidi100Client} can detect a missing
 * configuration at invocation time and refuse to dispatch a request rather than silently
 * signing with empty material.
 */
@Component
public class Kuaidi100Properties {

    private final String customer;
    private final String key;

    public Kuaidi100Properties(
            @Value("${MAIMAI_KUAIDI100_CUSTOMER:}") String customer,
            @Value("${MAIMAI_KUAIDI100_KEY:}") String key) {
        this.customer = customer == null ? "" : customer.trim();
        this.key = key == null ? "" : key.trim();
    }

    public String getCustomer() {
        return customer;
    }

    public String getKey() {
        return key;
    }

    public boolean isConfigured() {
        return !customer.isEmpty() && !key.isEmpty();
    }
}
