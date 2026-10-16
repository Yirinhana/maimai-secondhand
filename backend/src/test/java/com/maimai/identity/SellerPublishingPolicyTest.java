package com.maimai.identity;

import com.maimai.config.MaimaiProperties;
import com.maimai.identity.service.SellerPublishingPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SellerPublishingPolicyTest {
    @Test void mockConfigurationAloneCannotEnablePublicSimulationListings() {
        var properties=new MaimaiProperties();
        var environment=new MockEnvironment(); environment.setActiveProfiles("prod");
        var policy=new SellerPublishingPolicy(mock(JdbcTemplate.class),properties,environment);
        assertThat(properties.isExperienceListingEnabled()).isFalse();
        assertThat(policy.simulationEnabled()).isFalse();
        properties.setExperienceListingEnabled(true);
        assertThat(policy.simulationEnabled()).isTrue();
        properties.setExperienceListingEnabled(false);
        environment.setActiveProfiles("local");
        assertThat(policy.simulationEnabled()).isTrue();
        properties.getPayment().setChannel("wechat");
        assertThat(policy.simulationEnabled()).isFalse();
    }
}
