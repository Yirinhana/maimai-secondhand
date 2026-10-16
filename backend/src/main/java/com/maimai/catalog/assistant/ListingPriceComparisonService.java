package com.maimai.catalog.assistant;

import com.maimai.catalog.service.ProductImagePaths;
import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Current, eligible marketplace listings only. No model invocation, inferred trade prices or history fabrication. */
@Service
public class ListingPriceComparisonService {
    private static final int SAMPLE_LIMIT = 12;
    private static final Pattern DECORATION = words(
            "奶油白色", "深空灰色", "海军蓝色", "森林绿色", "鼠尾草绿色", "薄荷绿色", "焦糖棕色", "水洗蓝色", "米白色", "墨绿色", "蓝灰色", "黑黄色", "红灰色",
            "奶油白", "奶白色", "原木色", "浅木色", "橡木色", "深空灰", "海军蓝", "森林绿", "鼠尾草绿", "薄荷绿", "焦糖棕", "水洗蓝",
            "雾紫色", "雾蓝色", "浅灰色", "深灰色", "深蓝色", "浅蓝色", "浅粉色", "米灰色", "卡其色", "深棕色",
            "黑银色", "银黑色", "黑白色", "灰白色", "白灰色", "蓝黑色", "白橙色", "黑金色", "灰橙色", "白紫色", "黑红色",
            "黑色", "白色", "灰色", "蓝色", "红色", "绿色", "黄色", "紫色", "粉色", "橙色", "棕色", "银色", "金色", "沙色", "竹色",
            "墨绿", "米白", "驼色", "深灰", "深蓝", "浅灰", "浅粉", "黑银", "银黑", "黑白", "灰白", "白灰", "蓝黑", "白橙", "黑金", "蓝灰", "黑黄", "红灰",
            "二手", "闲置", "自用", "转让", "出售", "出一个", "出一台", "出一件", "九成新", "八成新", "全新", "高清");
    // Whole product nouns, longest first: never use a broad single character such as 包 or 锅.
    // Keep functional variants (mechanical keyboard, electric kettle, camera bag...) distinct.
    private static final Pattern PRODUCT_TYPE = words(
            "机械键盘", "键盘", "办公鼠标", "鼠标", "微单相机", "相机", "镜头", "摄像头", "游戏掌机", "笔记本", "平板电脑", "电子阅读器", "显示器", "数位板", "移动硬盘", "无线路由器", "充电宝", "手机", "耳机", "蓝牙音箱", "音箱", "麦克风",
            "空气炸锅", "电热水壶", "电饭煲", "炖锅", "汤锅", "炒锅", "蒸锅", "烤面包机", "咖啡机", "榨汁机", "吸尘器", "投影仪", "打印机", "风扇", "吹风机", "加湿器", "蒸汽熨斗", "电动缝纫机", "电子体重秤", "体重秤",
            "台灯", "落地阅读灯", "落地灯", "书架", "收纳架", "餐椅", "床头柜", "相框",
            "大衣", "外套", "夹克", "牛仔裤", "围巾", "运动鞋", "帆布包", "单肩包", "托特包", "通勤包", "登山背包", "背包", "登机箱", "钱包",
            "瑜伽垫", "羽毛球拍", "露营椅", "露营帐篷", "帐篷", "滑板", "哑铃", "足球", "三脚架", "望远镜", "骑行头盔", "轮滑鞋",
            "民谣吉他", "木吉他", "电吉他", "吉他", "尤克里里", "电子琴", "拼图", "工具箱", "摄影集");
    // Only strip these finite appearance/use descriptions after an explicit product type is found.
    // Unlisted descriptions, brands, model codes, sizes and meaningful suffixes remain required.
    private static final Pattern APPEARANCE = words(
            "金属", "折臂", "摇臂", "可调节", "圆罩", "桌面", "夹式", "长臂", "阅读", "拉链", "双口袋", "双耳",
            "中长款", "短款", "宽松", "哑光", "渐变", "圆角", "方形", "竹纹", "开放", "桌边", "收纳", "竹制", "窄",
            "加厚", "便携", "高背", "矮脚", "家用", "入门", "复古", "轻便", "森林湖景", "城市街景", "海边灯塔", "风景", "盒装");
    private static final Pattern WORD = Pattern.compile("[\\p{IsHan}]+|[a-z0-9][a-z0-9._-]*");
    private final JdbcTemplate db;
    private final ListingAssistantCatalog catalog;

    public ListingPriceComparisonService(JdbcTemplate db, ListingAssistantCatalog catalog) {
        this.db = db; this.catalog = catalog;
    }

    public record Item(long id, String title, long priceCents, String condition, String region,
                       String coverImage, String sellerNickname, String experienceSource, String url) {}
    public record Comparison(String currency, String priceBasis, int sampleCount, Long minCents, Long maxCents,
                             Long medianCents, String matchLevel, String keyword, List<Item> items,
                             Instant fetchedAt, String notice) {}

    @Transactional(readOnly = true)
    public Comparison compare(long viewer, String query, Long categoryId, Long excludeProductId) {
        String keyword = query == null ? "" : query.strip();
        if (keyword.length() > 120 || keyword.codePoints().anyMatch(Character::isISOControl))
            throw BizException.badRequest("COMPARISON_QUERY", "比价关键词请控制在120字以内，不要包含特殊控制字符");
        if ((categoryId != null && categoryId <= 0) || (excludeProductId != null && excludeProductId <= 0))
            throw BizException.badRequest("COMPARISON_CATEGORY", "分类或商品信息无效，请刷新后重新选择");
        if (keyword.isBlank() && categoryId == null)
            throw BizException.badRequest("COMPARISON_QUERY", "请先填写商品关键词或选择分类，再查看本站报价");

        Set<Long> eligibleCategories = categoryId == null ? catalog.active().keySet() : catalog.descendants(categoryId);
        List<String> terms = terms(keyword);
        if (!keyword.isBlank() && terms.isEmpty())
            throw BizException.badRequest("COMPARISON_QUERY", "请填写具体商品名称，例如相机、键盘或咖啡机");
        if (eligibleCategories.isEmpty()) return result(keyword, "NONE", List.of());

        var parameters = new ArrayList<Object>();
        var score = new StringBuilder("0");
        if (!keyword.isEmpty()) {
            score.append(" + CASE WHEN LOWER(p.title) LIKE ? ESCAPE '!' THEN 100 ELSE 0 END");
            parameters.add(like(Normalizer.normalize(keyword, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)));
            for (String term : terms) {
                score.append(" + CASE WHEN LOWER(p.title)=? THEN 60 ELSE 0 END"); parameters.add(term);
            }
        }
        var sql = new StringBuilder("""
            SELECT p.id,p.title,p.price_cents,p.item_condition,p.region,u.nickname,p.experience_source,
              (SELECT pi.path FROM product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort,pi.id LIMIT 1) AS cover_path,
            """).append(score).append(" AS relevance FROM products p JOIN users u ON u.id=p.seller_id WHERE ")
                .append("p.status='ON_SALE' AND p.stock_available>0 AND p.price_cents BETWEEN 1 AND 9007199254740991 ")
                .append("AND u.status='ACTIVE' AND p.seller_id<>? ")
                .append("AND EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='SELLER') ")
                .append("AND (SELECT sa.status FROM seller_applications sa WHERE sa.user_id=u.id ORDER BY sa.created_at DESC,sa.id DESC LIMIT 1)='APPROVED' ")
                .append("AND p.category_id IN (").append(String.join(",", java.util.Collections.nCopies(eligibleCategories.size(), "?"))).append(") ");
        parameters.add(viewer); parameters.addAll(eligibleCategories);
        if (excludeProductId != null) { sql.append("AND p.id<>? "); parameters.add(excludeProductId); }
        // All meaningful terms must occur in the title. Never silently replace a failed keyword search with unrelated goods.
        for (String term : terms) {
            sql.append("AND LOWER(p.title) LIKE ? ESCAPE '!' "); parameters.add(like(term));
        }
        sql.append("ORDER BY relevance DESC,p.updated_at DESC,p.id DESC LIMIT ").append(SAMPLE_LIMIT);
        List<Item> samples = db.query(sql.toString(), (row, number) -> new Item(row.getLong("id"), row.getString("title"),
                row.getLong("price_cents"), row.getString("item_condition"), row.getString("region"),
                ProductImagePaths.publicUrl(row.getString("cover_path")), row.getString("nickname"),
                row.getString("experience_source"), "/products/" + row.getLong("id")), parameters.toArray());
        samples.sort(Comparator.comparingLong(Item::priceCents).thenComparingLong(Item::id));
        String level = samples.isEmpty() ? "NONE" : keyword.isBlank() ? "CATEGORY" : categoryId == null ? "KEYWORD" : "CATEGORY_KEYWORD";
        return result(keyword, level, samples);
    }

    static List<String> terms(String keyword) {
        String cleaned = DECORATION.matcher(Normalizer.normalize(keyword, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll(" ");
        var found = new LinkedHashSet<String>();
        var types = PRODUCT_TYPE.matcher(cleaned);
        int end = 0;
        boolean typed = false;
        while (types.find()) {
            typed = true;
            addTerms(found, APPEARANCE.matcher(cleaned.substring(end, types.start())).replaceAll(" "), true);
            addTerm(found, types.group());
            end = types.end();
        }
        if (typed) addTerms(found, APPEARANCE.matcher(cleaned.substring(end)).replaceAll(" "), true);
        else addTerms(found, cleaned, false); // No recognized noun: keep the old conservative phrase match.
        return List.copyOf(found);
    }

    private static void addTerms(Set<String> found, String text, boolean preserveSingleCharacter) {
        var matcher = WORD.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (preserveSingleCharacter || value.length() >= 2) addTerm(found, value);
        }
    }

    private static void addTerm(Set<String> found, String value) {
        found.add(value);
        if (found.size() > 12) throw BizException.badRequest("COMPARISON_QUERY", "比价关键词较多，请保留商品类型和主要型号后重试");
    }

    private static Pattern words(String... values) {
        return Pattern.compile(java.util.Arrays.stream(values).sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote).collect(Collectors.joining("|")));
    }

    private static String like(String value) { return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }

    private static Comparison result(String keyword, String level, List<Item> samples) {
        int count = samples.size();
        Long minimum = count == 0 ? null : samples.getFirst().priceCents();
        Long maximum = count == 0 ? null : samples.getLast().priceCents();
        Long median = null;
        if (count > 0) {
            long low = samples.get((count - 1) / 2).priceCents(), high = samples.get(count / 2).priceCents();
            median = low + (high - low + 1) / 2; // Exact median, half a cent rounds up without adding two potentially large prices.
        }
        String notice = "仅比较本站当前在售商品的挂牌价，不是成交价、全网报价或历史走势；运费未计入，成色、配件和交付方式需逐项核对。";
        if (count == 0) notice = "暂时没有匹配的在售商品，请调整商品名称或分类后重试；不会以无关商品或虚构价格补足样本。";
        else if (count == 1) notice += "目前仅1个样本，只供单点参考，不足以代表普遍价格。";
        else notice += "图中按报价从低到高排列，统计仅针对这" + count + "个样本。";
        if (samples.stream().anyMatch(item -> item.experienceSource() != null && !item.experienceSource().isBlank()))
            notice += "含体验来源商品，报价用于流程体验，不表示真实成交。";
        return new Comparison("CNY", "ASKING", count, minimum, maximum, median, level, keyword, List.copyOf(samples), Instant.now(), notice);
    }
}
