package com.maimai.catalog.service;

import com.maimai.catalog.domain.Category;
import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.StockLog;
import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.catalog.dto.CatalogDtos.ProductCreateRequest;
import com.maimai.catalog.dto.CatalogDtos.ProductUpdateRequest;
import com.maimai.catalog.dto.CatalogDtos.SellerProductItem;
import com.maimai.catalog.dto.CatalogDtos.StockAdjustRequest;
import com.maimai.catalog.repo.CategoryRepository;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.catalog.repo.StockLogRepository;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.domain.SellerApplication;
import com.maimai.identity.repo.SellerApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 卖家商品管理：新建/修改/库存/提交审核/下架/我的列表。 */
@Service
@Transactional
public class SellerProductService {

    static final int MAX_IMAGES = 9;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final StockLogRepository stockLogRepository;
    private final CategoryRepository categoryRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final ProductAssembler assembler;
    private final ProductRevisionService revisions;
    private final CategoryAvailability categoryAvailability;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public SellerProductService(ProductRepository productRepository,
                                ProductImageRepository productImageRepository,
                                StockLogRepository stockLogRepository,
                                CategoryRepository categoryRepository,
                                SellerApplicationRepository sellerApplicationRepository,
                                ProductAssembler assembler,ProductRevisionService revisions,
                                CategoryAvailability categoryAvailability,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.stockLogRepository = stockLogRepository;
        this.categoryRepository = categoryRepository;
        this.sellerApplicationRepository = sellerApplicationRepository;
        this.assembler = assembler;
        this.revisions=revisions;this.categoryAvailability=categoryAvailability;this.jdbc=jdbc;
    }

    public Long create(Long userId, ProductCreateRequest request) {
        requireActiveUser(userId);
        boolean submit = Boolean.TRUE.equals(request.submit());
        if (submit) {
            requireQualifiedSeller(userId);
        } else {
            requireApprovedSeller(userId);
        }
        Product product = new Product();
        product.setSellerId(userId);
        applyFields(product, request.title(), request.categoryId(), request.description(),
                request.condition(), request.defects(), request.priceCents(), request.region(),
                request.deliveryMethods(), request.freightCents(), request.returnPromise());
        product.setStockAvailable(request.stock());
        setCoverageAndLocation(product,request.shippingProvinces(),request.latitude(),request.longitude());
        product.setStatus(Product.Status.DRAFT);
        productRepository.save(product);
        revisions.record(product,userId,"CREATE");
        if (submit) {
            submitForReview(product);
        }
        return product.getId();
    }

    public void update(Long userId, Long productId, ProductUpdateRequest request) {
        Product product = ownedProduct(userId, productId);
        if (request.stock() != null) {
            throw BizException.badRequest("STOCK_FIELD_NOT_ALLOWED", "库存调整请使用 /stock 端点");
        }
        requireApprovedSeller(userId);
        Product.Condition condition = ProductQueryService.parseCondition(request.condition());
        String deliveryMethods = joinDeliveryMethods(request.deliveryMethods());
        validateCategory(request.categoryId());
        ProductLocation.validate(request.latitude(),request.longitude());
        String shipping=String.join(",",ProductShipping.normalize(request.shippingProvinces()));
        boolean keyChanged = ProductAssembler.keyFieldsChanged(product, request.title(), request.categoryId(),
                request.description(), condition, request.defects(), request.priceCents(),
                deliveryMethods, request.freightCents())
                || !java.util.Objects.equals(product.getRegion(), request.region())
                || !java.util.Objects.equals(product.getReturnPromise(), request.returnPromise())
                || !java.util.Objects.equals(product.getShippingProvinces(),shipping)
                || !sameCoordinate(product.getLatitude(),request.latitude())
                || !sameCoordinate(product.getLongitude(),request.longitude());
        if(keyChanged)revisions.baseline(product,userId);
        product.setTitle(request.title());
        product.setCategoryId(request.categoryId());
        product.setDescription(request.description());
        product.setItemCondition(condition);
        product.setDefects(request.defects());
        product.setPriceCents(request.priceCents());
        product.setRegion(request.region());
        product.setDeliveryMethods(deliveryMethods);
        product.setFreightCents(request.freightCents());
        product.setReturnPromise(request.returnPromise());
        product.setShippingProvinces(shipping);product.setLatitude(request.latitude());product.setLongitude(request.longitude());
        if (keyChanged && product.getStatus() == Product.Status.ON_SALE) {
            // 在售商品关键信息变化 → 转复审，暂停新成交
            product.setStatus(Product.Status.CHANGES_REVIEW);
        }
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        if(keyChanged)revisions.record(product,userId,"UPDATE");
    }

    public void adjustStock(Long userId, Long productId, StockAdjustRequest request) {
        Product product = ownedProduct(userId, productId);
        int delta = request.delta();
        if (delta == 0) {
            throw BizException.badRequest("STOCK_DELTA_INVALID", "库存调整量不能为 0");
        }
        long newAvailable = (long) product.getStockAvailable() + delta;
        if (newAvailable < 0 || newAvailable > Integer.MAX_VALUE) {
            throw BizException.badRequest("STOCK_INSUFFICIENT", "调整后库存不能小于 0");
        }
        product.setStockAvailable((int) newAvailable);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        StockLog log = new StockLog();
        log.setProductId(product.getId());
        log.setDeltaAvailable(delta);
        log.setReason("SELLER_ADJUST");
        stockLogRepository.save(log);
    }

    public void submit(Long userId, Long productId) {
        Product product = ownedProduct(userId, productId);
        requireQualifiedSeller(userId);
        if (product.getStatus() != Product.Status.DRAFT
                && product.getStatus() != Product.Status.REJECTED
                && product.getStatus() != Product.Status.OFF_SHELF) {
            throw BizException.badRequest("PRODUCT_STATUS_INVALID", "当前状态不可提交审核");
        }
        submitForReview(product);
    }

    public void offShelf(Long userId, Long productId) {
        Product product = ownedProduct(userId, productId);
        if (product.getStatus() != Product.Status.ON_SALE) {
            throw BizException.badRequest("PRODUCT_STATUS_INVALID", "仅在售商品可下架");
        }
        revisions.baseline(product,userId);
        product.setStatus(Product.Status.OFF_SHELF);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        revisions.record(product,userId,"OFF_SHELF");
    }

    /** 图片上传/删除导致关键信息变化时的复审规则：在售 → CHANGES_REVIEW。 */
    void onImagesChanged(Product product) {
        if (product.getStatus() == Product.Status.ON_SALE) {
            product.setStatus(Product.Status.CHANGES_REVIEW);
            product.setUpdatedAt(Instant.now());
            productRepository.save(product);
        }
        revisions.record(product,product.getSellerId(),"IMAGES_CHANGED");
    }

    @Transactional(readOnly = true)
    public com.maimai.catalog.dto.CatalogDtos.SellerProductDetail detailMine(Long userId, Long productId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (!userId.equals(product.getSellerId())) throw BizException.notFound("商品不存在");
        return new com.maimai.catalog.dto.CatalogDtos.SellerProductDetail(product.getId(), product.getCategoryId(),
                product.getTitle(), product.getDescription(), product.getItemCondition().name(), product.getDefects(),
                product.getPriceCents(), product.getStockAvailable(), product.getStockReserved(), product.getStockSold(),
                product.getRegion(), ProductAssembler.splitDeliveryMethods(product.getDeliveryMethods()),
                product.getFreightCents(), product.getReturnPromise(), product.getStatus().name(), product.getReviewReason(),
                productImageRepository.findByProductIdOrderBySort(productId).stream().map(image ->
                    new com.maimai.catalog.dto.CatalogDtos.ImageItem(image.getId(), ProductImagePaths.publicUrl(image.getPath()), image.getSort())).toList(),
                ProductShipping.split(product.getShippingProvinces()),product.getLatitude(),product.getLongitude());
    }

    @Transactional(readOnly = true)
    public PageResult<SellerProductItem> listMine(Long userId, String status, Integer page, Integer size) {
        Product.Status filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = Product.Status.valueOf(status.trim().toUpperCase());
            } catch (RuntimeException ex) {
                throw BizException.badRequest("STATUS_INVALID", "非法的商品状态");
            }
        }
        Product.Status statusFilter = filter;
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("sellerId"), userId));
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Product> result = productRepository.findAll(spec, PageRequest.of(
                ProductQueryService.pageIndex(page), ProductQueryService.pageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResult.from(result.map(this::toSellerItem));
    }

    private SellerProductItem toSellerItem(Product product) {
        return new SellerProductItem(product.getId(), product.getTitle(), product.getPriceCents(),
                product.getItemCondition().name(), product.getRegion(),
                assembler.coverImage(product.getId()), product.getStockAvailable(),
                product.getStockReserved(), product.getStockSold(), product.getStatus().name(),
                product.getReviewReason(), product.getFreightCents(),
                ProductAssembler.splitDeliveryMethods(product.getDeliveryMethods()),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    private void submitForReview(Product product) {
        validateCategory(product.getCategoryId());
        int imageCount = productImageRepository.findByProductIdOrderBySort(product.getId()).size();
        if (imageCount < 1) {
            throw BizException.badRequest("PRODUCT_IMAGES_REQUIRED", "提交审核前需上传至少 1 张商品图片");
        }
        if (imageCount > MAX_IMAGES) {
            throw BizException.badRequest("PRODUCT_IMAGES_TOO_MANY", "商品图片最多 " + MAX_IMAGES + " 张");
        }
        revisions.baseline(product,product.getSellerId());
        product.setStatus(Product.Status.PENDING_REVIEW);
        product.setReviewReason(null);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        revisions.record(product,product.getSellerId(),"SUBMIT_REVIEW");
    }

    Product ownedProduct(Long userId, Long productId) {
        requireActiveUser(userId);
        Product product = productRepository.lockById(productId)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (!product.getSellerId().equals(userId)) throw BizException.forbidden("无权操作该商品");
        return product;
    }

    private void applyFields(Product product, String title, Long categoryId, String description,
                             String condition, String defects, Long priceCents, String region,
                             List<String> deliveryMethods, Long freightCents, String returnPromise) {
        validateCategory(categoryId);
        product.setTitle(title);
        product.setCategoryId(categoryId);
        product.setDescription(description);
        product.setItemCondition(ProductQueryService.parseCondition(condition));
        product.setDefects(defects);
        product.setPriceCents(priceCents);
        product.setRegion(region);
        product.setDeliveryMethods(joinDeliveryMethods(deliveryMethods));
        product.setFreightCents(freightCents);
        product.setReturnPromise(returnPromise);
    }

    private void validateCategory(Long categoryId) {
        categoryAvailability.requireActive(categoryId);
    }

    public void requireActiveUser(Long userId) {
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE' FOR UPDATE",userId).isEmpty())throw BizException.forbidden("账号当前不能操作商品");
    }
    private void setCoverageAndLocation(Product product,List<String> provinces,java.math.BigDecimal latitude,java.math.BigDecimal longitude) {
        ProductLocation.validate(latitude,longitude);product.setShippingProvinces(String.join(",",ProductShipping.normalize(provinces)));
        product.setLatitude(latitude);product.setLongitude(longitude);
    }
    private static boolean sameCoordinate(java.math.BigDecimal left,java.math.BigDecimal right){return left==null?right==null:right!=null&&left.compareTo(right)==0;}

    static String joinDeliveryMethods(List<String> methods) {
        List<String> normalized = ProductAssembler.normalizeDeliveryMethods(methods);
        if (normalized.isEmpty() || !ProductAssembler.DELIVERY_METHODS.containsAll(normalized)) {
            throw BizException.badRequest("DELIVERY_METHOD_INVALID", "交付方式仅支持 EXPRESS/MEETUP");
        }
        return String.join(",", normalized);
    }

    /** 卖家准入：最新申请须 APPROVED。 */
    private SellerApplication requireApprovedSeller(Long userId) {
        SellerApplication application = sellerApplicationRepository
                .findTopByUserIdOrderByCreatedAtDescIdDesc(userId)
                .orElseThrow(() -> BizException.forbidden("需先完成卖家准入审核"));
        if (application.getStatus() != SellerApplication.Status.APPROVED) {
            throw BizException.forbidden("需先完成卖家准入审核");
        }
        return application;
    }

    /** 发布可成交商品：还须收款渠道资格 QUALIFIED。 */
    private void requireQualifiedSeller(Long userId) {
        SellerApplication application = requireApprovedSeller(userId);
        if (application.getChannelStatus() != SellerApplication.ChannelStatus.QUALIFIED) {
            throw BizException.forbidden("收款渠道资格未开通，暂不可发布可成交商品");
        }
    }
}
