package com.maimai.support.workflow;

import java.util.Locale;

/** Reviewed, non-personalized starting points. These never claim a model or a business action ran. */
final class WorkflowGuidance {
    static final String MODEL = "platform-guide";
    private WorkflowGuidance() { }

    static String answer(String stage, String question, String draft) {
        String q = question == null ? "" : question.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        boolean undecided = q.matches("(?s).*(不知道|不清楚|没想好|没有想好|不知道该|不知道应该|不懂).*(卖|发布|闲置).*" )
                || q.matches("(?s).*(卖啥|卖什么|选一件闲置|整理闲置从哪|有什么可以卖).*" );
        if (("LISTING".equals(stage) || "GUIDE".equals(stage)) && undecided) {
            return "可以，先不用急着填商品资料。看看最近三个月没用、功能正常、你有权转让的东西：读完的书、闲置键盘或耳机、干净完整的小家居，通常比较容易整理和描述。\n\n先挑一件你最了解的，检查功能、拍下瑕疵，再参考站内同类商品的标价；标价不代表成交价。别急着为了出售再进货。\n\n你手边更接近哪一类：书籍、数码，还是生活用品？";
        }
        if ("LISTING".equals(stage) && q.isBlank() && (draft == null || draft.isBlank())) {
            return "我们可以从一件小闲置开始，不需要一次想清所有信息。先选一件你不再用、能正常使用且有权转让的物品；拍正面、背面和瑕疵，填写的内容会自动保存为草稿。\n\n你想先整理哪件物品？如果还没想好，点“我不知道卖什么”，一起挑一个方向。";
        }
        if ("LISTING".equals(stage)) {
            if (q.equals("我有一些读完的书")) return "那就从一本你熟悉的书开始。先看看书页是否完整、有无笔记或折角，再拍封面、版权页和有使用痕迹的地方。版本、配套资料是否齐全会影响买家的选择；参考同版书的标价时，也把运费考虑进去。\n\n你想先卖哪本书？说出书名就能继续，其他信息可以边看边补。";
            if (q.equals("我有闲置耳机")) return "耳机可以先做一次小检查：两边能否正常出声、连接是否稳定，再看看充电和外壳情况。清洁后拍下实物和配件，有电池衰减或其他问题就如实写，不要只写‘正常使用’。\n\n它是有线还是无线耳机？先告诉我这一点就好。";
            if (q.equals("我有生活用品")) return "可以先选一件体积不大、清洁方便的小物，比如收纳用品或台灯。检查是否破损、缺件；把尺寸和使用痕迹拍清楚。较大或易碎的物品可以考虑同城面交，先别急着承诺包邮。\n\n你手边具体是哪件物品？";
        }
        if ("GUIDE".equals(stage) && "第一次买二手，从哪里开始？".equals(question)) {
            return "先确定用途和预算，再用分类、价格及交付方式缩小范围。打开商品后重点看瑕疵、配件、卖家信誉和含运费总价；有疑问可点“联系卖家”，商品链接会一起带入私信。\n\n确认后再下单。本站体验付款只更新站内状态，不扣真实资金；遇到问题从订单申请售后，不需要另扫个人收款码。\n\n你准备找哪类物品？";
        }
        if ("GUIDE".equals(stage) && "收到的东西有问题怎么办？".equals(question)) {
            return "先保留商品现状、包装、照片和沟通记录，别急着确认收货。打开“我的交易”中的对应订单，选择申请售后，写清问题及希望的处理方式；退货请等双方在售后中确认后再寄出。\n\n协商不成时可按页面申请平台介入。麦仔可以帮你整理事实，但退款和责任要经过订单流程核实。";
        }
        return null;
    }

    static String unavailable(String stage) {
        return switch (stage) {
            case "LISTING" -> "暂时连不上深度分析，你可以先继续整理草稿：先写物品是什么，再写实际使用情况和已知瑕疵；不知道的品牌或参数先留空。填写内容会保留，想选品时可以直接问“我不知道卖什么”。";
            case "PRODUCT" -> "暂时无法进一步分析这件商品。你仍可查看实拍和缺陷说明，再通过“联系卖家”核对功能、配件和交付方式。不要把标价或他人的评价当作真伪保证。";
            case "ORDER", "AFTERSALE" -> "暂时无法进一步分析。请以页面最新订单状态和操作按钮为准：交付问题先留存证据，再通过订单售后沟通；不要因回复延迟而重复付款或寄件。需要人工帮助时可携带订单号提交工单。";
            case "REVIEW" -> "暂时无法进一步分析。可以先写这次交易中实际发生的事，例如商品与描述是否一致、沟通和交付情况，再写你的感受。不要放入电话、地址等隐私；需要退款请另外走订单售后。";
            default -> "暂时无法进一步分析。买东西可从分类和预算开始，卖东西可先保存一份闲置草稿；已有交易问题请从对应订单申请售后，也可以转人工客服。";
        };
    }
}
