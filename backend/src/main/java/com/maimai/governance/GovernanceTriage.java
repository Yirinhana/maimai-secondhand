package com.maimai.governance;

import java.util.Locale;
import java.util.regex.Pattern;

/** Cheap, deliberately broad candidate filter. A non-match is not a compliance decision. */
public final class GovernanceTriage {
    private GovernanceTriage() { }
    private static final Pattern CLUES=Pattern.compile(
        "投诉|举报|骗|假货|冒牌|仿品|违禁|违约|货不对板|描述不符|不发货|不退款|拒绝.{0,6}(退款|退货)|失联|拉黑|威胁|辱骂|脏话|泄露|曝光|人肉|隐私|定金|订金|私下|私自|站外|加[微vV]|微信|转账|收款码|差评|损坏|破损|缺件|故障|维修|争议|纠纷|未收到|没收到|无法使用|不能用|退款|退货|售后|scam|fraud|counterfeit|refund|off.?platform|\\[联系资料已隐藏\\]|\\[密钥已隐藏\\]|\\[敏感凭据已隐藏\\]",
        Pattern.CASE_INSENSITIVE);
    public static boolean candidate(GovernanceSnapshot.Snapshot source) {
        if(source.type().equals("REPORT")||source.type().equals("AFTERSALE"))return true;
        if(source.type().equals("ORDER_REVIEW")&&Pattern.compile("评分=[123](?:；|$)").matcher(source.facts()).find())return true;
        return CLUES.matcher(source.text().toLowerCase(Locale.ROOT)).find();
    }
}
