package com.maimai.catalog.service;

import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.dto.CatalogDtos.ImageItem;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.identity.domain.User;
import com.maimai.identity.repo.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 商品视图装配：封面图、卖家昵称、交付方式拆分等公共转换，供查询/卖家/后台服务复用。 */
@Component
public class ProductAssembler {

    public static final Set<String> DELIVERY_METHODS = Set.of("EXPRESS", "MEETUP");

    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    public ProductAssembler(ProductImageRepository productImageRepository, UserRepository userRepository) {
        this.productImageRepository = productImageRepository;
        this.userRepository = userRepository;
    }

    /** 封面图：sort 最小图片的 path，无图时为 null。 */
    public String coverImage(Long productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySort(productId);
        return images.isEmpty() ? null : ProductImagePaths.publicUrl(images.getFirst().getPath());
    }

    public Map<Long, String> coverImages(Collection<Long> productIds) {
        Map<Long, String> result = new HashMap<>();
        for (Long id : productIds) {
            result.put(id, coverImage(id));
        }
        return result;
    }

    public List<ImageItem> images(Long productId) {
        List<ImageItem> result = new ArrayList<>();
        for (ProductImage image : productImageRepository.findByProductIdOrderBySort(productId)) {
            result.add(new ImageItem(image.getId(), ProductImagePaths.publicUrl(image.getPath()), image.getSort()));
        }
        return result;
    }

    public Map<Long, String> nicknames(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (User user : userRepository.findAllById(userIds)) {
            result.put(user.getId(), user.getNickname());
        }
        return result;
    }

    public static List<String> splitDeliveryMethods(String deliveryMethods) {
        if (deliveryMethods == null || deliveryMethods.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : deliveryMethods.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    /** 归一化交付方式：去重、保持声明顺序、校验枚举；非法值由调用方转为 badRequest。 */
    public static List<String> normalizeDeliveryMethods(Collection<String> methods) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String method : methods) {
            if (method != null) {
                normalized.add(method.trim().toUpperCase());
            }
        }
        return List.copyOf(normalized);
    }

    public static boolean keyFieldsChanged(Product p, String title, Long categoryId, String description,
                                           Product.Condition condition, String defects, long priceCents,
                                           String deliveryMethods, long freightCents) {
        return !p.getTitle().equals(title)
                || !p.getCategoryId().equals(categoryId)
                || !nullSafeEquals(p.getDescription(), description)
                || p.getItemCondition() != condition
                || !nullSafeEquals(p.getDefects(), defects)
                || p.getPriceCents() != priceCents
                || !p.getDeliveryMethods().equals(deliveryMethods)
                || p.getFreightCents() != freightCents;
    }

    private static boolean nullSafeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
