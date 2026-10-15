package com.maimai.support.chat;

import com.maimai.common.security.SecurityUtils;
import com.maimai.support.service.SupportFaq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only site navigation. Links and product facts come from our database, never the model. */
@Service
public class SupportNavigation {
    public record Resource(String kind, String title, String path, Long priceCents,
                           String imagePath, String detail, boolean experience) { }
    public record Reply(String answer, List<Resource> resources) { }
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Pattern BUDGET=Pattern.compile("(?:预算|不超过|最多|低于)\\s*(\\d{1,6}(?:\\.\\d{1,2})?)\\s*元?|(?<![\\d.])(\\d{1,6}(?:\\.\\d{1,2})?)\\s*元?\\s*(?:以内|以下)");
    private static final List<String> GOODS=List.of("空气炸锅","咖啡机","电饭煲","吸尘器","显示器","平板电脑","笔记本电脑","羽绒服","冲锋衣","自行车","滑板","相机","镜头","外套","夹克","大衣","毛衣","衬衫","短袖","裙子","裤子","鞋","背包","书包","行李箱","耳机","键盘","鼠标","音箱","手机","电脑","平板","手表","手环","台灯","桌子","椅子","书架","教材","书籍","书","篮球","足球","羽毛球","网球","帐篷","吉他","玩具");
    private final JdbcTemplate jdbc;
    public SupportNavigation(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true)
    public Optional<Reply> resolve(String question) {
        String q=question.strip();
        if(has(q,"用户条款","用户协议","隐私","个人信息保护","服务条款"))
            return Optional.of(new Reply("可以直接打开下方的用户协议与交易售后规则。页面支持搜索条款，隐私、交易和售后约定都可查看原文。",List.of(link("用户协议与交易售后规则","/policies","查看完整条款与条款搜索"))));
        if(has(q,"退款","退货","售后","服务费","手续费","支付规则","交付规则","收货规则")) {
            var topic=has(q,"服务费","手续费")?SupportFaq.Topic.FEES:has(q,"交付规则","收货规则")?SupportFaq.Topic.DELIVERY:has(q,"支付规则")?SupportFaq.Topic.PAYMENT:SupportFaq.Topic.AFTERSALES;
            return Optional.of(new Reply(topic.answer+"\n涉及你的订单时，请从订单详情进入对应流程；需要人工协助也可提交工单。",List.of(link("查看我的订单","/orders","在订单详情中跟进交付、申请售后"),link("查看完整交易规则","/policies","规则原文"),link("帮助与人工工单","/support","提交问题并关联本人订单"))));
        }
        if(has(q,"我的订单","查订单","订单在哪","订单入口"))
            return Optional.of(new Reply("打开“我的订单”，选择需要跟进的那笔交易即可查看进度和可用操作。这里提供入口，不会替你确认收货或付款。",List.of(link("我的订单","/orders","查看付款、交付和售后进度"))));
        if(has(q,"怎么发布","如何发布","发布入口","卖闲置"))
            return Optional.of(new Reply("从发布闲置开始，可以先保存草稿，补齐商品资料后再提交审核。未通过卖家认证时也能整理草稿。",List.of(link("发布闲置","/publish","填写资料或继续保存的草稿"),link("买卖指南","/official/buying-and-selling-guide","了解从发布到交付的步骤"))));
        if(has(q,"人工客服","联系人工","举报入口","投诉入口"))
            return Optional.of(new Reply("可以从帮助与人工工单提交问题并关联本人订单。商品、评论和求购内容的举报，请在原内容旁选择举报，方便管理员核对来源。",List.of(link("帮助与人工工单","/support","提交材料并查看处理进度"))));
        if((GOODS.stream().anyMatch(q::contains)&&has(q,"推荐","想买","找","搜","需要","有没有","看看")
                ||has(q,"推荐商品","推荐好物","看看商品","找商品","买东西","找好物","推荐闲置")
                ||q.matches(".*(?:找|搜).*[“\"「].*[”\"」].*"))&&!has(q,"不要推荐","不用推荐","不需要推荐"))
            return Optional.of(products(q));
        return Optional.empty();
    }

    private Reply products(String q) {
        String keyword=GOODS.stream().filter(q::contains).findFirst().orElse("");
        // Explicit quoted keywords support brands and uncommon items without extra inference calls.
        var quoted=Pattern.compile("[“\"「]([^”\"」]{1,30})[”\"」]").matcher(q);
        if(quoted.find()) keyword=quoted.group(1).strip();
        if(keyword.isBlank()&&!has(q,"推荐商品","推荐好物","看看商品","找商品","买东西","找好物","推荐闲置"))
            return new Reply("你想找哪种闲置？可以说“推荐外套，预算200元以内”，或给商品名称加引号，例如：找“机械键盘”。我会查站内在售商品，并给你可直接打开的入口。",List.of(link("浏览全部商品","/search","也可以按分类和预算自己筛选")));
        Long max=null;var budget=BUDGET.matcher(q);
        if(budget.find()) max=new BigDecimal(budget.group(1)!=null?budget.group(1):budget.group(2)).movePointRight(2).longValueExact();
        String escaped=keyword.replace("!","!!").replace("%","!%").replace("_","!_");
        var args=new ArrayList<Object>();args.add(SecurityUtils.currentUserId());args.add("%"+escaped+"%");
        String ceiling="";
        if(max!=null){ceiling=" AND p.price_cents<=?";args.add(max);}
        var cards=jdbc.query("""
                SELECT p.id,p.title,p.price_cents,p.region,p.item_condition,p.experience_source,
                  (SELECT path FROM product_images WHERE product_id=p.id ORDER BY sort,id LIMIT 1) image_path
                FROM products p JOIN users u ON u.id=p.seller_id JOIN categories c ON c.id=p.category_id
                WHERE p.status='ON_SALE' AND p.stock_available>0 AND p.seller_id<>?
                  AND u.status='ACTIVE' AND c.status='ACTIVE'
                  AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SELLER')
                  AND (SELECT s.status FROM seller_applications s WHERE s.user_id=u.id ORDER BY s.created_at DESC,s.id DESC LIMIT 1)='APPROVED'
                  AND p.title LIKE ? ESCAPE '!'
                """+ceiling+" ORDER BY p.created_at DESC,p.id DESC LIMIT 3",
                (rs,n)->new Resource("PRODUCT",rs.getString("title"),"/products/"+rs.getLong("id"),rs.getLong("price_cents"),rs.getString("image_path"),rs.getString("region"),rs.getString("experience_source")!=null),args.toArray());
        var resources=new ArrayList<>(cards);
        String path="/search?keyword="+URLEncoder.encode(keyword,StandardCharsets.UTF_8);
        if(max!=null)path+="&maxPrice="+BigDecimal.valueOf(max,2).toPlainString();
        resources.add(link("继续筛选商品",path,"调整价格、成色和交付方式"));
        if(cards.isEmpty())resources.add(link("发布求购","/community/demands","写下需要的物品，等待卖家回应"));
        String subject=keyword.isBlank()?"闲置":"“"+keyword+"”";
        return new Reply(cards.isEmpty()?"暂时没有找到符合条件且可购买的"+subject+"。可以调整搜索条件，或去求购社区发布需求。":
                "找到以下与"+subject+"相关的在售商品，点击卡片查看详情。价格和库存是本次查询时的信息；尺码、瑕疵和配送范围，请在下单前与卖家确认。",List.copyOf(resources));
    }
    private static Resource link(String title,String path,String detail){return new Resource("PAGE",title,path,null,null,detail,false);}
    private static boolean has(String text,String...terms){return Arrays.stream(terms).anyMatch(text::contains);}
    static String encode(List<Resource> resources){return JSON.writeValueAsString(resources);}
    static List<Resource> decode(String value){return value==null?List.of():List.of(JSON.readValue(value,Resource[].class));}
}
