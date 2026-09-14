package com.maimai.catalog.service;

import com.maimai.catalog.domain.Category;
import com.maimai.catalog.domain.Product;
import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.catalog.dto.CatalogDtos.ProductDetail;
import com.maimai.catalog.dto.CatalogDtos.ProductSummary;
import com.maimai.catalog.dto.CatalogDtos.SellerBrief;
import com.maimai.catalog.dto.CatalogDtos.SellerProfile;
import com.maimai.catalog.repo.CategoryRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.common.BizException;
import com.maimai.common.security.AuthenticatedUser;
import com.maimai.identity.domain.User;
import com.maimai.identity.repo.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ProductAssembler assembler;

    public ProductQueryService(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               UserRepository userRepository,
                               ProductAssembler assembler) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.assembler = assembler;
    }

    /** 公开商品搜索：固定 ON_SALE，支持关键词/分类（含子分类）/成色/价格区间/地区/交付方式筛选。 */
    public PageResult<ProductSummary> search(String keyword, Long categoryId, String condition,
                                             Long minPriceCents, Long maxPriceCents, String region,
                                             String deliveryMethod, String sort, Integer page, Integer size,
                                             Double originLatitude,Double originLongitude) {
        boolean nearby="distance_asc".equals(sort);
        ProductLocation.validateOrigin(originLatitude,originLongitude,nearby);
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), Product.Status.ON_SALE));
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("title"), "%" + escapeLike(keyword.trim()) + "%", '\\'));
            }
            if (categoryId != null) {
                Set<Long> ids = categoryWithDescendants(categoryId);
                predicates.add(root.get("categoryId").in(ids));
            }
            if (condition != null && !condition.isBlank()) {
                predicates.add(cb.equal(root.get("itemCondition"), parseCondition(condition)));
            }
            if (minPriceCents != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceCents"), minPriceCents));
            }
            if (maxPriceCents != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceCents"), maxPriceCents));
            }
            if (region != null && !region.isBlank()) {
                predicates.add(cb.equal(root.get("region"), region.trim()));
            }
            if (deliveryMethod != null && !deliveryMethod.isBlank()) {
                String method = deliveryMethod.trim().toUpperCase();
                if (!ProductAssembler.DELIVERY_METHODS.contains(method)) {
                    throw BizException.badRequest("DELIVERY_METHOD_INVALID", "非法的交付方式");
                }
                predicates.add(cb.like(root.get("deliveryMethods"), "%" + method + "%"));
            }
            if(nearby && query.getResultType()!=Long.class && query.getResultType()!=long.class) {
                var lat=cb.function("radians",Double.class,root.get("latitude"));
                var lon=cb.function("radians",Double.class,root.get("longitude"));
                double originLat=Math.toRadians(originLatitude),originLon=Math.toRadians(originLongitude);
                var cosine=cb.sum(cb.prod(cb.function("sin",Double.class,lat),Math.sin(originLat)),
                        cb.prod(cb.prod(cb.function("cos",Double.class,lat),Math.cos(originLat)),cb.function("cos",Double.class,cb.diff(lon,originLon))));
                var bounded=cb.function("least",Double.class,cb.literal(1.0),cb.function("greatest",Double.class,cb.literal(-1.0),cosine));
                var missing=cb.<Integer>selectCase().when(cb.isNull(root.get("latitude")),1).otherwise(0);
                query.orderBy(cb.asc(missing),cb.asc(cb.function("acos",Double.class,bounded)),cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Product> result = productRepository.findAll(spec, nearby?PageRequest.of(pageIndex(page),pageSize(size)):pageable(sort, page, size));
        return toSummaryPage(result,originLatitude,originLongitude);
    }

    /** 商品详情：非 ON_SALE 仅卖家本人或后台角色可见，否则按不存在处理（不泄露存在性）。 */
    public ProductDetail detail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (product.getStatus() != Product.Status.ON_SALE) {
            AuthenticatedUser viewer = currentOrNull();
            boolean allowed = viewer != null
                    && (viewer.id().equals(product.getSellerId()) || viewer.isAdmin());
            if (!allowed) {
                throw BizException.notFound("商品不存在");
            }
        }
        User seller = userRepository.findById(product.getSellerId()).orElse(null);
        String nickname = seller != null ? seller.getNickname() : "";
        return new ProductDetail(product.getId(), product.getTitle(), product.getDescription(),
                product.getItemCondition().name(), product.getDefects(), product.getPriceCents(),
                product.getStockAvailable(), product.getRegion(),
                ProductAssembler.splitDeliveryMethods(product.getDeliveryMethods()),
                product.getFreightCents(), product.getReturnPromise(), product.getStatus().name(),
                assembler.images(product.getId()), new SellerBrief(product.getSellerId(), nickname),
                product.getCreatedAt(),ProductShipping.split(product.getShippingProvinces()),product.getLatitude(),product.getLongitude());
    }

    public SellerProfile sellerProfile(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> BizException.notFound("卖家不存在"));
        Specification<Product> onSale = (root, query, cb) -> cb.and(
                cb.equal(root.get("sellerId"), sellerId),
                cb.equal(root.get("status"), Product.Status.ON_SALE));
        long onSaleCount = productRepository.count(onSale);
        return new SellerProfile(seller.getId(), seller.getNickname(), seller.getCreatedAt(), onSaleCount);
    }

    public PageResult<ProductSummary> sellerProducts(Long sellerId, Integer page, Integer size) {
        if (!userRepository.existsById(sellerId)) {
            throw BizException.notFound("卖家不存在");
        }
        Specification<Product> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("sellerId"), sellerId),
                cb.equal(root.get("status"), Product.Status.ON_SALE));
        Page<Product> result = productRepository.findAll(spec,
                PageRequest.of(pageIndex(page), pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return toSummaryPage(result);
    }

    /** 批量装配摘要：卖家昵称与封面图按页集中查询，避免逐条 N+1。 */
    private PageResult<ProductSummary> toSummaryPage(Page<Product> page) {
        return toSummaryPage(page,null,null);
    }
    private PageResult<ProductSummary> toSummaryPage(Page<Product> page,Double originLatitude,Double originLongitude) {
        List<Product> products = page.getContent();
        Set<Long> sellerIds = new LinkedHashSet<>();
        Set<Long> productIds = new LinkedHashSet<>();
        for (Product product : products) {
            sellerIds.add(product.getSellerId());
            productIds.add(product.getId());
        }
        Map<Long, String> nicknames = assembler.nicknames(sellerIds);
        Map<Long, String> covers = assembler.coverImages(productIds);
        return PageResult.from(page.map(product -> new ProductSummary(product.getId(), product.getTitle(),
                product.getPriceCents(), product.getItemCondition().name(), product.getRegion(),
                covers.get(product.getId()), product.getStockAvailable(), product.getSellerId(),
                nicknames.getOrDefault(product.getSellerId(), ""), product.getFreightCents(),
                ProductAssembler.splitDeliveryMethods(product.getDeliveryMethods()),
                ProductLocation.distance(product.getLatitude(),product.getLongitude(),originLatitude,originLongitude))));
    }

    /** 目标分类及其全部子孙分类 id（基于 ACTIVE 分类）。 */
    private Set<Long> categoryWithDescendants(Long categoryId) {
        List<Category> categories = categoryRepository.findByStatusOrderBySort(Category.Status.ACTIVE);
        Map<Long, List<Long>> childrenOf = new java.util.HashMap<>();
        for (Category category : categories) {
            if (category.getParentId() != null) {
                childrenOf.computeIfAbsent(category.getParentId(), k -> new ArrayList<>()).add(category.getId());
            }
        }
        Set<Long> result = new LinkedHashSet<>();
        List<Long> pending = new ArrayList<>(List.of(categoryId));
        while (!pending.isEmpty()) {
            Long current = pending.removeLast();
            if (result.add(current)) {
                pending.addAll(childrenOf.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    static Product.Condition parseCondition(String condition) {
        try {
            return Product.Condition.valueOf(condition.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw BizException.badRequest("CONDITION_INVALID", "非法的成色取值");
        }
    }

    static Pageable pageable(String sort, Integer page, Integer size) {
        Sort order = switch (sort == null || sort.isBlank() ? "time_desc" : sort.trim()) {
            case "time_desc" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "priceCents");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "priceCents");
            default -> throw BizException.badRequest("SORT_INVALID", "非法的排序方式");
        };
        return PageRequest.of(pageIndex(page), pageSize(size), order.and(Sort.by("id")));
    }

    static int pageIndex(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw BizException.badRequest("PAGE_INVALID", "page 不能为负数");
        }
        return page;
    }

    static int pageSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1) {
            throw BizException.badRequest("SIZE_INVALID", "size 至少为 1");
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static AuthenticatedUser currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user ? user : null;
    }
}
