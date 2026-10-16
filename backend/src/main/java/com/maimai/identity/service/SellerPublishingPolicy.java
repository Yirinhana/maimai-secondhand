package com.maimai.identity.service;

import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One server-side policy for publishing and its UI explanation; never invents channel qualification. */
@Service
public class SellerPublishingPolicy {
    private final JdbcTemplate db;
    private final MaimaiProperties properties;
    private final Environment environment;

    public SellerPublishingPolicy(JdbcTemplate db, MaimaiProperties properties, Environment environment) {
        this.db = db;
        this.properties = properties;
        this.environment = environment;
    }

    public record Capability(String mode, boolean canPublish, String message) {}
    private record Eligibility(boolean active, boolean seller, String status, String channelStatus) {
        boolean approved() { return active && seller && "APPROVED".equals(status); }
    }

    public boolean simulationEnabled() {
        return properties.isExperienceListingEnabled()
                || ("mock".equals(properties.getPayment().getChannel())
                && environment.acceptsProfiles(Profiles.of("local", "test")));
    }

    public Capability capability(long userId) {
        Eligibility eligibility = eligibility(userId);
        boolean simulated = simulationEnabled();
        String mode = simulated ? "SIMULATED" : "REAL_PAYMENT";
        if (!eligibility.approved()) {
            return new Capability(mode, false, eligibility.active()
                    ? "需先完成平台卖家审核，审核通过后才能提交商品。"
                    : "账号当前不能发布商品，请联系平台客服。");
        }
        if (simulated) {
            return new Capability(mode, true,
                    "已通过平台卖家审核，可以发布商品并提交审核。当前使用站内模拟交易，不发生真实扣款或收款，无需开通真实支付渠道。");
        }
        if (!"QUALIFIED".equals(eligibility.channelStatus())) {
            return new Capability(mode, false,
                    "已通过平台卖家审核，可以保存草稿；真实交易还需完成收款渠道核验后才能提交商品。");
        }
        return new Capability(mode, true,
                "平台卖家审核和收款渠道核验均已通过，可以提交商品审核；实际付款仍以渠道开通状态为准。");
    }

    public void requireApprovedSeller(long userId) {
        Eligibility eligibility = eligibility(userId);
        if (!eligibility.active()) throw BizException.forbidden("账号当前不能操作商品");
        if (!eligibility.approved()) throw BizException.forbidden("需先完成卖家准入审核");
    }

    public void requirePublish(long userId) {
        Capability capability = capability(userId);
        if (!capability.canPublish()) throw BizException.forbidden(capability.message());
    }

    /** Existing product provenance determines its mode, independently of a later configuration switch. */
    public void requireExistingPublication(long userId, String experienceSource) {
        requireApprovedSeller(userId);
        if (com.maimai.catalog.domain.ExperienceProductSource.supportsCheckout(experienceSource)) return;
        if (experienceSource != null) throw BizException.forbidden("该商品来源不支持重新发布，请新建商品");
        if (!"QUALIFIED".equals(eligibility(userId).channelStatus())) {
            throw BizException.forbidden("该商品属于正式交易，需完成收款渠道核验；体验发布请另建商品，不会自动更改原商品来源");
        }
    }

    private Eligibility eligibility(long userId) {
        return db.query("""
                SELECT u.status='ACTIVE' active,
                    EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SELLER') seller,
                    a.status application_status, a.channel_status
                FROM users u LEFT JOIN seller_applications a ON a.id=(
                    SELECT latest.id FROM seller_applications latest WHERE latest.user_id=u.id
                    ORDER BY latest.created_at DESC, latest.id DESC LIMIT 1)
                WHERE u.id=?
                """, (row, n) -> new Eligibility(row.getBoolean("active"), row.getBoolean("seller"),
                row.getString("application_status"), row.getString("channel_status")), userId)
                .stream().findFirst().orElse(new Eligibility(false, false, null, null));
    }
}
