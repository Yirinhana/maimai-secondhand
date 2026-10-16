package com.maimai.catalog.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/** Executes the real comparison SQL against isolated MySQL; every fixture is rolled back. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ComparisonQueryIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired ListingPriceComparisonService comparisons;
    @MockitoBean ListingVisionModel model;
    long viewer, seller, category;

    @BeforeEach void prepare() {
        assertThat(db.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimai_test");
        viewer = user("比价查询者", "USER");
        seller = user("比价测试卖家", "SELLER");
        db.update("INSERT INTO seller_applications(user_id,status,channel_status) VALUES(?,'APPROVED','QUALIFIED')", seller);
        category = insert("INSERT INTO categories(name,status) VALUES('独立比价测试类','ACTIVE')");
    }

    @ParameterizedTest
    @CsvSource({
            "米白金属折臂阅读台灯,墨绿圆罩桌面台灯,米白家用工具箱",
            "米白色金属台灯,墨绿色圆罩桌面台灯,米白色家用工具箱",
            "奶油白双耳炖锅,灰色铸铁双耳炖锅,奶油白空气炸锅",
            "焦糖棕拉链单肩包,墨绿拉链帆布单肩包,焦糖棕烤面包机",
            "原木色三层窄书架,三层竹制收纳书架,原木色三层餐椅",
            "鼠尾草绿加厚瑜伽垫,雾紫色 6mm 瑜伽垫,鼠尾草绿露营帐篷",
            "原木色民谣吉他,棕色渐变 41 寸民谣吉他,原木色电子琴",
            "白色可调节摇臂台灯,墨绿圆罩桌面台灯,白色可调节书架",
            "森林湖景拼图,城市街景 1000 片拼图,森林湖景摄影集",
            "蓝灰家用工具箱,红灰便携维修工具箱,蓝灰桌面台灯",
            "灰白机械键盘,黑色 104 键有线机械键盘,灰白薄膜键盘"
    })
    void naturalTitleMatchesItsProductTypeWithoutMatchingOnlyTheSameColor(String query, String comparable, String unrelated) {
        long expected = product(comparable, 9000);
        product(unrelated, 100);
        var result = comparisons.compare(viewer, query, category, null);
        assertThat(result.items()).extracting(ListingPriceComparisonService.Item::id).containsExactly(expected);
        assertThat(result.minCents()).isEqualTo(9000);
        assertThat(result.sampleCount()).isEqualTo(1);
        verifyNoInteractions(model);
    }

    @Test void explicitBrandAndModelRemainRequiredIncludingNormalizedFullWidthText() {
        long correct = product("罗技 K380 蓝牙键盘", 10000);
        product("罗技 K480 蓝牙键盘", 4000);
        product("其他品牌 K380 键盘", 3000);
        var result = comparisons.compare(viewer, "罗技 Ｋ３８０ 键盘", category, null);
        assertThat(result.items()).extracting(ListingPriceComparisonService.Item::id).containsExactly(correct);
        assertThat(ListingPriceComparisonService.terms("红米 K70 手机")).containsExactly("红米", "k70", "手机");
    }

    @Test void cameraPriceQualifierAndUnknownTextNeverDisappear() {
        long cheap = product("比较相机低价", 10000);
        product("比较相机高价", 30000);
        assertThat(comparisons.compare(viewer, "相机低价", category, null).items())
                .extracting(ListingPriceComparisonService.Item::id).containsExactly(cheap);
        assertThat(comparisons.compare(viewer, "比较相机低价", category, null).sampleCount()).isEqualTo(1);
        product("墨绿圆罩桌面台灯", 5000);
        var missing = comparisons.compare(viewer, "没有记载的特殊型号台灯", category, null);
        assertThat(missing.sampleCount()).isZero();
        assertThat(missing.minCents()).isNull();
        assertThat(missing.maxCents()).isNull();
        assertThat(missing.medianCents()).isNull();
        assertThat(ListingPriceComparisonService.terms("黑色神秘收藏纪念品")).containsExactly("神秘收藏纪念品");
    }

    @Test void shortSuffixesSizesAndModelWildcardCharactersRemainRequired() {
        product("黑色相机", 10000);
        assertThat(comparisons.compare(viewer, "相机包", category, null).sampleCount()).isZero();
        assertThat(ListingPriceComparisonService.terms("大衣 M 码")).containsExactly("大衣", "m", "码");
        long exact = product("定制 A_2 键盘", 9000);
        product("定制 A12 键盘", 100);
        assertThat(comparisons.compare(viewer, "定制 A_2 键盘", category, null).items())
                .extracting(ListingPriceComparisonService.Item::id).containsExactly(exact);
        assertThat(ListingPriceComparisonService.terms("深蓝色 8mm 瑜伽垫"))
                .containsExactly("8mm", "瑜伽垫");
    }

    private long user(String name, String role) {
        long id = insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-hash',?,'ACTIVE')",
                UUID.randomUUID() + "@comparison-test.invalid", name);
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)", id, role);
        return id;
    }

    private long product(String title, long price) {
        return insert("INSERT INTO products(seller_id,category_id,title,description,item_condition,price_cents,stock_available,region,delivery_methods,status) VALUES(?,?,?,'独立事务测试商品','GOOD',?,1,'上海','MEETUP','ON_SALE')",
                seller, category, title, price);
    }

    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
