package com.maimai.identity.service;

import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyConsentService {
    public static final String CURRENT_VERSION="2026-09-14-local";
    private final JdbcTemplate jdbc;
    public PolicyConsentService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public CurrentPolicy current() {
        return new CurrentPolicy(CURRENT_VERSION,"麦麦二手用户协议与交易售后规则",true,
                "仅供本地演示的已审阅草案，未代表公开运营条款、经营主体资质或实际支付渠道已经就绪。");
    }
    public void validate(Boolean accepted,String version) {
        if(!Boolean.TRUE.equals(accepted))throw BizException.badRequest("POLICY_CONSENT_REQUIRED","请阅读并同意当前用户协议与交易售后规则");
        if(!CURRENT_VERSION.equals(version))throw BizException.badRequest("POLICY_VERSION_CHANGED","条款版本不一致，请刷新并重新阅读确认");
    }
    @Transactional
    public void record(long userId,String version) {
        validate(true,version);
        jdbc.update("INSERT INTO user_policy_acceptances(user_id,policy_version) VALUES(?,?)",userId,version);
    }
    public record CurrentPolicy(String version,String title,boolean localDraft,String notice) {}
}
