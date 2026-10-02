package com.maimai.catalog.admin;

import com.maimai.admin.domain.AdminAuditLog;
import com.maimai.admin.repo.AdminAuditLogRepository;
import com.maimai.catalog.admin.AdminProductDtos.AdminProductItem;
import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductReviewLog;
import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.catalog.repo.ProductReviewLogRepository;
import com.maimai.catalog.service.ProductAssembler;
import com.maimai.common.BizException;
import com.maimai.notification.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 后台商品审核：待审核队列与审核动作，全程留痕（审核日志 + 后台审计 + 站内通知）。 */
@Service
@Transactional
public class AdminProductService {

    private static final Set<Product.Status> REVIEWABLE =
            Set.of(Product.Status.PENDING_REVIEW, Product.Status.CHANGES_REVIEW);

    private final ProductRepository productRepository;
    private final ProductReviewLogRepository productReviewLogRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final NotificationService notificationService;
    private final ProductAssembler assembler;
    private final com.maimai.catalog.service.ProductRevisionService revisions;
    private final com.maimai.catalog.service.CategoryAvailability categories;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AdminProductService(ProductRepository productRepository,
                               ProductReviewLogRepository productReviewLogRepository,
                               AdminAuditLogRepository adminAuditLogRepository,
                               NotificationService notificationService,
                               ProductAssembler assembler,com.maimai.catalog.service.ProductRevisionService revisions,
                               com.maimai.catalog.service.CategoryAvailability categories,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.productRepository = productRepository;
        this.productReviewLogRepository = productReviewLogRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.notificationService = notificationService;
        this.assembler = assembler;
        this.revisions=revisions;this.categories=categories;this.jdbc=jdbc;
    }

    /** 待审核队列：status 限 PENDING_REVIEW/CHANGES_REVIEW，默认 PENDING_REVIEW，按提交时间升序。 */
    @Transactional(readOnly = true)
    public PageResult<AdminProductItem> listPending(String status, Integer page, Integer size) {
        Product.Status filter = Product.Status.PENDING_REVIEW;
        if (status != null && !status.isBlank()) {
            try {
                filter = Product.Status.valueOf(status.trim().toUpperCase());
            } catch (RuntimeException ex) {
                throw BizException.badRequest("STATUS_INVALID", "非法的商品状态");
            }
            if (!REVIEWABLE.contains(filter)) {
                throw BizException.badRequest("STATUS_INVALID", "仅支持 PENDING_REVIEW/CHANGES_REVIEW");
            }
        }
        Product.Status statusFilter = filter;
        Specification<Product> spec = (root, query, cb) -> cb.equal(root.get("status"), statusFilter);
        int pageIndex = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null ? 20 : Math.min(Math.max(size, 1), 50);
        Page<Product> result = productRepository.findAll(spec,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.ASC, "createdAt")));
        Set<Long> sellerIds = new LinkedHashSet<>();
        Set<Long> productIds = new LinkedHashSet<>();
        for (Product product : result.getContent()) {
            sellerIds.add(product.getSellerId());
            productIds.add(product.getId());
        }
        Map<Long, String> nicknames = assembler.nicknames(sellerIds);
        Map<Long, String> covers = assembler.coverImages(productIds);
        return PageResult.from(result.map(product -> new AdminProductItem(product.getId(), product.getTitle(),
                product.getSellerId(), nicknames.getOrDefault(product.getSellerId(), ""),
                product.getCategoryId(), product.getPriceCents(), product.getItemCondition().name(),
                product.getRegion(), product.getStatus().name(), product.getReviewReason(),
                covers.get(product.getId()),
                ProductAssembler.splitDeliveryMethods(product.getDeliveryMethods()),
                product.getCreatedAt(), product.getUpdatedAt())));
    }

    /** 审核：PENDING_REVIEW/CHANGES_REVIEW → ON_SALE（通过）或 REJECTED（拒绝，须给理由）。 */
    public void review(Long adminId, Long productId, boolean approve, String reason) {
        var sellers=jdbc.queryForList("SELECT seller_id FROM products WHERE id=?",Long.class,productId);
        if(sellers.isEmpty())throw BizException.notFound("商品不存在");
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE' FOR UPDATE",sellers.getFirst()).isEmpty())throw BizException.conflict("SELLER_INACTIVE","卖家账号已停用，不能审核上架");
        Product product = productRepository.lockById(productId)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (!REVIEWABLE.contains(product.getStatus())) {
            throw BizException.conflict("PRODUCT_NOT_IN_REVIEW", "该商品当前不在待审核状态");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw BizException.badRequest("REVIEW_REASON_REQUIRED", "拒绝时必须填写理由");
        }
        if(approve) {
            categories.requireActive(product.getCategoryId());
            if(assembler.images(productId).isEmpty())throw BizException.badRequest("PRODUCT_IMAGES_REQUIRED","商品至少需要一张图片才可上架");
        }
        revisions.baseline(product,adminId);
        Product.Status from = product.getStatus();
        Product.Status to = approve ? Product.Status.ON_SALE : Product.Status.REJECTED;
        product.setStatus(to);
        product.setReviewReason(reason);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        revisions.record(product,adminId,approve?"APPROVE":"REJECT");

        ProductReviewLog reviewLog = new ProductReviewLog();
        reviewLog.setProductId(product.getId());
        reviewLog.setReviewerId(adminId);
        reviewLog.setAction(approve ? ProductReviewLog.Action.APPROVE : ProductReviewLog.Action.REJECT);
        reviewLog.setReason(reason);
        reviewLog.setFromStatus(from.name());
        reviewLog.setToStatus(to.name());
        productReviewLogRepository.save(reviewLog);

        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.setAdminId(adminId);
        auditLog.setAction(approve ? "PRODUCT_REVIEW_APPROVE" : "PRODUCT_REVIEW_REJECT");
        auditLog.setTargetType("product");
        auditLog.setTargetId(product.getId());
        auditLog.setReason(reason);
        auditLog.setBeforeState(from.name());
        auditLog.setAfterState(to.name());
        adminAuditLogRepository.save(auditLog);

        String title = approve ? "商品审核通过" : "商品审核未通过";
        StringBuilder content = new StringBuilder()
                .append("商品《").append(product.getTitle()).append("》")
                .append(approve ? "已通过审核并上架。" : "未通过审核。");
        if (reason != null && !reason.isBlank()) {
            content.append("理由：").append(reason);
        }
        notificationService.notify(product.getSellerId(), "PRODUCT_REVIEW", title, content.toString(),"/publish/"+product.getId());
    }
}
