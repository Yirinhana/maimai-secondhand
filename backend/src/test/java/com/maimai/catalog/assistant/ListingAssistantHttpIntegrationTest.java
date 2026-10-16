package com.maimai.catalog.assistant;

import com.maimai.common.BizException;
import com.maimai.common.GlobalExceptionHandler;
import com.maimai.common.PersonalMediaService;
import com.maimai.common.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Real isolated MySQL and real controllers/services; only the billed model is replaced. Fixtures roll back. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListingAssistantHttpIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired JsonMapper json;
    @Autowired ListingAssistantController controller;
    @Autowired ListingPriceComparisonService comparisons;
    @Autowired PersonalMediaService media;
    @MockitoBean ListingVisionModel model;
    MockMvc http;
    long buyer, seller, other, root, leaf, cheapest, middle, expensive;

    @BeforeEach void setup() {
        assertThat(db.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("maimai_test");
        buyer = user("比价买家", "USER"); seller = user("相机小铺", "SELLER"); other = user("其他账号", "USER");
        root = insert("INSERT INTO categories(name,status) VALUES('比价测试数码','ACTIVE')");
        leaf = insert("INSERT INTO categories(name,parent_id,status) VALUES('比价测试相机',?,'ACTIVE')", root);
        approve(seller);
        middle = product(seller, leaf, "比较相机中价", 20000);
        cheapest = product(seller, leaf, "比较相机低价", 10000);
        expensive = product(seller, leaf, "比较相机高价", 30000);
        when(model.configured()).thenReturn(true); when(model.modelName()).thenReturn("local-controlled-test");
        when(model.recognize(any(), any())).thenReturn(new ListingVisionModel.Recognition("黑色相机", leaf, "照片可见黑色外壳", List.of("黑色外壳"), List.of("请核实型号与功能")));
        db.update("INSERT INTO listing_ai_daily_usage(usage_date,scope_id,calls) VALUES(?,0,0) ON DUPLICATE KEY UPDATE calls=0", today());
        db.update("UPDATE listing_ai_runs SET status='FAILED' WHERE status='PROCESSING'");
        login(buyer, "USER");
        http = standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void normalBuyerCanReadSortedCurrentAskingPricesAndRealLinkedFieldsWithoutModel() throws Exception {
        db.update("UPDATE products SET experience_source='maimai-experience-045' WHERE id=?", cheapest);
        db.update("INSERT INTO product_images(product_id,path,sort) VALUES(?,'products/compare-front.jpg',1),(?,'/uploads/products/compare-cover.jpg',0)", cheapest, cheapest);
        String body = http.perform(get("/api/v1/me/listing-assistant/comparables").param("query", "相机").param("categoryId", String.valueOf(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("CNY")).andExpect(jsonPath("$.priceBasis").value("ASKING"))
                .andExpect(jsonPath("$.sampleCount").value(3)).andExpect(jsonPath("$.minCents").value(10000))
                .andExpect(jsonPath("$.medianCents").value(20000)).andExpect(jsonPath("$.maxCents").value(30000))
                .andExpect(jsonPath("$.matchLevel").value("CATEGORY_KEYWORD"))
                .andExpect(jsonPath("$.items[0].id").value(cheapest)).andExpect(jsonPath("$.items[1].id").value(middle))
                .andExpect(jsonPath("$.items[2].id").value(expensive)).andExpect(jsonPath("$.items[0].sellerNickname").value("相机小铺"))
                .andExpect(jsonPath("$.items[0].coverImage").value("/uploads/products/compare-cover.jpg"))
                .andExpect(jsonPath("$.items[0].experienceSource").value("maimai-experience-045"))
                .andExpect(jsonPath("$.items[0].url").value("/products/" + cheapest)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("体验来源商品", "挂牌价").doesNotContain("password_hash", "seller_id", "@listing-test.invalid");
        verifyNoInteractions(model);
    }

    @Test void rootCategoryIncludesOnlyActiveDescendantsAndExplicitCategorySearchIsHonest() {
        long deeper = insert("INSERT INTO categories(name,parent_id,status) VALUES('微单',?,'ACTIVE')", leaf);
        long extra = product(seller, deeper, "比较相机微单", 25000);
        long unrelated = insert("INSERT INTO categories(name,status) VALUES('不相关分类','ACTIVE')");
        product(seller, unrelated, "比较相机跨分类", 500);
        var result = comparisons.compare(buyer, null, root, null);
        assertThat(result.sampleCount()).isEqualTo(4);
        assertThat(result.items()).extracting(ListingPriceComparisonService.Item::id).contains(extra);
        assertThat(result.matchLevel()).isEqualTo("CATEGORY");
        assertThat(result.medianCents()).isEqualTo(22500);
        verifyNoInteractions(model);
    }

    @Test void hiddenOutOfStockDisabledUnapprovedAndNonSellerListingsAreExcluded() {
        long hidden = product(seller, leaf, "比较相机待审", 1); db.update("UPDATE products SET status='PENDING_REVIEW' WHERE id=?", hidden);
        long offShelf = product(seller, leaf, "比较相机下架", 1); db.update("UPDATE products SET status='OFF_SHELF' WHERE id=?", offShelf);
        long empty = product(seller, leaf, "比较相机售罄", 1); db.update("UPDATE products SET stock_available=0 WHERE id=?", empty);
        long disabled = user("禁用店铺", "SELLER"); approve(disabled); product(disabled, leaf, "比较相机禁用", 1); db.update("UPDATE users SET status='DISABLED' WHERE id=?", disabled);
        long noRole = user("非卖家", "USER"); approve(noRole); product(noRole, leaf, "比较相机无角色", 1);
        long pending = user("待审核卖家", "SELLER"); product(pending, leaf, "比较相机未认证", 1);
        var result = comparisons.compare(buyer, "相机", root, null);
        assertThat(result.items()).extracting(ListingPriceComparisonService.Item::id).containsExactly(cheapest, middle, expensive);
    }

    @Test void latestSellerApplicationOverridesHistoricalApproval() {
        db.update("INSERT INTO seller_applications(user_id,status,created_at) VALUES(?,'REJECTED',UTC_TIMESTAMP(6)+INTERVAL 1 MINUTE)", seller);
        assertThat(comparisons.compare(buyer, "相机", root, null).items()).isEmpty();
        db.update("INSERT INTO seller_applications(user_id,status,created_at) VALUES(?,'APPROVED',UTC_TIMESTAMP(6)+INTERVAL 2 MINUTE)", seller);
        assertThat(comparisons.compare(buyer, "相机", root, null).sampleCount()).isEqualTo(3);
    }

    @Test void disabledAncestorAndCategoryCyclesNeverLeakIntoKeywordSamples() {
        db.update("UPDATE categories SET status='DISABLED' WHERE id=?", root);
        assertThat(comparisons.compare(buyer, "比较相机", null, null).items()).isEmpty();
        assertThatThrownBy(() -> comparisons.compare(buyer, null, leaf, null)).isInstanceOf(BizException.class).hasMessageContaining("分类已调整");
        db.update("UPDATE categories SET status='ACTIVE',parent_id=? WHERE id=?", leaf, root);
        assertThat(comparisons.compare(buyer, "比较相机", null, null).items()).isEmpty();
    }

    @Test void excludesAllOwnListingsAndTheEditedProduct() {
        approve(buyer); db.update("INSERT INTO user_roles(user_id,role) VALUES(?,'SELLER')", buyer);
        long own = product(buyer, leaf, "比较相机本人商品", 500);
        var result = comparisons.compare(buyer, "相机", root, middle);
        assertThat(result.items()).extracting(ListingPriceComparisonService.Item::id).containsExactly(cheapest, expensive).doesNotContain(own, middle);
        assertThat(result.medianCents()).isEqualTo(20000);
    }

    @Test void unknownKeywordsNeverFallBackToUnrelatedPricesAndSingleSampleIsLabeled() {
        var empty = comparisons.compare(buyer, "完全无关的咖啡机", root, null);
        assertThat(empty.sampleCount()).isZero(); assertThat(empty.items()).isEmpty(); assertThat(empty.minCents()).isNull();
        assertThat(empty.medianCents()).isNull(); assertThat(empty.maxCents()).isNull(); assertThat(empty.matchLevel()).isEqualTo("NONE");
        var single = comparisons.compare(buyer, "比较相机低价", root, null);
        assertThat(single.sampleCount()).isEqualTo(1); assertThat(single.minCents()).isEqualTo(10000); assertThat(single.medianCents()).isEqualTo(10000);
        assertThat(single.notice()).contains("仅1个样本", "单点参考");
    }

    @Test void preservesMeaningfulTermsAndRemovesAppearanceOnlyModifiers() {
        product(seller, leaf, "黑色书桌", 100);
        assertThat(comparisons.compare(buyer, "黑色相机", root, null).items()).hasSize(3);
        assertThat(comparisons.compare(buyer, "相机低价", root, null).items()).extracting(ListingPriceComparisonService.Item::id).containsExactly(cheapest);
        assertThat(comparisons.compare(buyer, "' OR 1=1 --", root, null).items()).isEmpty();
    }

    @Test void capsNearestSamplesBeforeSortingPricesAndDoesNotInventExtraSamples() {
        for (int i = 0; i < 17; i++) product(seller, leaf, "比价镜头样本" + i, 1000 + i * 100);
        var result = comparisons.compare(buyer, "比价镜头", root, null);
        assertThat(result.sampleCount()).isEqualTo(12); assertThat(result.items()).hasSize(12);
        assertThat(result.items()).extracting(ListingPriceComparisonService.Item::priceCents).isSorted();
        assertThat(result.notice()).contains("这12个样本");
        long secondLeaf = insert("INSERT INTO categories(name,parent_id,status) VALUES('二样本',?,'ACTIVE')", root);
        product(seller, secondLeaf, "一分钱差异A", 101); product(seller, secondLeaf, "一分钱差异B", 102);
        assertThat(comparisons.compare(buyer, null, secondLeaf, null).medianCents()).isEqualTo(102);
    }

    @Test void authenticatedControllerRejectsBadQueryAndUnauthenticatedAccess() throws Exception {
        http.perform(get("/api/v1/me/listing-assistant/comparables")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请先填写商品关键词或选择分类，再查看本站报价"));
        http.perform(get("/api/v1/me/listing-assistant/comparables").param("query", "长".repeat(121))).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/me/listing-assistant/comparables").param("categoryId", "-1")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/me/listing-assistant/comparables").param("categoryId", "not-a-number")).andExpect(status().isBadRequest());
        SecurityContextHolder.clearContext();
        http.perform(get("/api/v1/me/listing-assistant/availability")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/me/listing-assistant/comparables").param("query", "相机")).andExpect(status().isUnauthorized());
        verifyNoInteractions(model);
    }

    @Test void staleOwnerAndMissingOwnerAreRejectedBeforeLookingAtPhotosOrModel() throws Exception {
        http.perform(post("/api/v1/me/listing-assistant/recognize").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ownerId", other, "imageIds", List.of(UUID.randomUUID().toString()), "requestKey", UUID.randomUUID().toString()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LISTING_AI_ACCOUNT_CHANGED"));
        http.perform(post("/api/v1/me/listing-assistant/recognize").contentType(MediaType.APPLICATION_JSON).content("{\"imageIds\":[],\"requestKey\":\"request-123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LISTING_AI_OWNER"));
        verifyNoInteractions(model);
    }

    @Test void otherAccountsCannotRecognizePrivatePhotosOrReadSavedRecognition() throws Exception {
        var foreign = media.upload(other, "DRAFT", photo());
        http.perform(post("/api/v1/me/listing-assistant/recognize").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ownerId", buyer, "imageIds", List.of(foreign.id()), "requestKey", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        var recognition = new ListingVisionModel.Recognition("私有相机草稿", leaf, "私有说明", List.of(), List.of("请核实"));
        long run = insert("INSERT INTO listing_ai_runs(user_id,request_key,image_hash,image_ids_json,model,status,result_json) VALUES(?,?,?,'[]','controlled','SUCCEEDED',?)",
                other, UUID.randomUUID().toString(), "0".repeat(64), json.writeValueAsString(recognition));
        http.perform(get("/api/v1/me/listing-assistant/runs/" + run)).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("这次识图记录不存在"));
        assertThat(db.queryForObject("SELECT COALESCE(SUM(calls),0) FROM listing_ai_daily_usage WHERE scope_id=0 AND usage_date=?", Integer.class, today())).isZero();
        verifyNoInteractions(model);
    }

    @Test void ownDraftRecognitionRoundTripKeepsIdentityAndReusesResultWithoutExtraModelCost() throws Exception {
        var image = media.upload(buyer, "DRAFT", photo());
        http.perform(get("/api/v1/me/listing-assistant/availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.maxImages").value(3));
        String body = http.perform(post("/api/v1/me/listing-assistant/recognize").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ownerId", buyer, "imageIds", List.of(image.id()), "requestKey", UUID.randomUUID().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recognition.categoryId").value(leaf)).andExpect(jsonPath("$.cached").value(false))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long id = json.readTree(body).path("id").asLong();
        http.perform(get("/api/v1/me/listing-assistant/runs/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.recognition.title").value("黑色相机"));
        http.perform(post("/api/v1/me/listing-assistant/recognize").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ownerId", buyer, "imageIds", List.of(image.id()), "requestKey", UUID.randomUUID().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.cached").value(true));
        verify(model, times(1)).recognize(any(), any());
        assertThat(db.queryForObject("SELECT calls FROM listing_ai_daily_usage WHERE usage_date=? AND scope_id=?", Integer.class, today(), buyer)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE seller_id=?", Integer.class, buyer)).isZero();
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    private void approve(long seller) { db.update("INSERT INTO seller_applications(user_id,status,channel_status) VALUES(?,'APPROVED','QUALIFIED')", seller); }
    private long user(String name, String role) {
        long id = insert("INSERT INTO users(email,password_hash,nickname,status) VALUES(?,'test-hash',?,'ACTIVE')", UUID.randomUUID() + "@listing-test.invalid", name);
        db.update("INSERT INTO user_roles(user_id,role) VALUES(?,?)", id, role); return id;
    }
    private long product(long seller, long category, String title, long price) {
        return insert("INSERT INTO products(seller_id,category_id,title,description,item_condition,price_cents,stock_available,region,delivery_methods,status) VALUES(?,?,?,'外观与描述一致','GOOD',?,2,'上海','MEETUP','ON_SALE')", seller, category, title, price);
    }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder(); db.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement;
        }, key); return Objects.requireNonNull(key.getKey()).longValue();
    }
    private void login(long id, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(id, "local-test", "测试主体", Set.of(role)), null, List.of()));
    }
    private MockMultipartFile photo() throws Exception {
        var bytes = VisionImagePayloadTest.jpeg(4, 4);
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
    }
}
