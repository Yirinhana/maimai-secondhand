package com.maimai.support.service;

import java.util.Arrays;
import java.util.List;

/** Reviewed project rules only. Ticket text and private order details never enter this knowledge set. */
public final class SupportFaq {
    private SupportFaq() { }
    public static final String NOTICE = "【AI规则说明】仅供理解平台规则，不能代替人工决定退款、放款或争议处理；需要个案协助请联系人工客服。\n";
    public enum Topic {
        FEES("平台服务费", "平台费为每个卖家子订单实际商品成交金额的0.03%，不含运费，四舍五入到分，可为0元。卖家另承担渠道费用，实际费率待渠道确认。全额退款退全部平台费；部分退款按剩余商品款重算退差额。"),
        PAYMENT("支付与退款", "下单后30分钟内付款。只有支付渠道确认后才显示支付或退款成功。当前真实微信支付资格尚待开通，本地模拟交易不代表真实资金处理。客服AI不能执行付款、退款、放款或分账。"),
        AFTERSALES("售后规则", "常规售后入口保留至收货后15个自然日，收货前可申请；卖家48小时内答复，批准退货后7日内寄回。超期或特殊问题可联系人工，常规时限不排除法定权利。仅退款、退货退款、全额及部分退款需按订单售后流程处理。"),
        DELIVERY("交付规则", "卖家72小时内发货。快递发货10天后，无售后及已知物流异常时可自动收货；物流异常或售后暂停。面交由买家出示10分钟有效的一次性交付码，卖家核验，不采用快递自动收货。"),
        ACCOUNT("账号和人工协助", "使用邮箱验证码注册、密码登录和邮箱找回。不要向任何客服提供密码、邮箱授权码或支付私钥。可创建人工工单并关联本人订单，只有本人、获授权客服及超级管理员可查看；运营没有客服权限。首次人工响应目标为3个工作日。" );
        public final String title;
        public final String answer;
        Topic(String title, String answer) { this.title = title; this.answer = answer; }
    }
    public record Item(Topic topic, String title, String answer) { }
    public static List<Item> items() { return Arrays.stream(Topic.values()).map(t -> new Item(t, t.title, t.answer)).toList(); }
}
