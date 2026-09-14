package com.maimai.trade.service;

import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.repo.UserRepository;
import com.maimai.trade.domain.CartItem;
import com.maimai.trade.dto.TradeDtos.CartItemDto;
import com.maimai.trade.repo.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 购物车：不占库存；同 user+product+deliveryMethod 合并数量；失效商品前端标注。 */
@Service
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    public CartService(CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       ProductImageRepository productImageRepository,
                       UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CartItemDto> list(Long userId) {
        List<CartItem> items = new ArrayList<>(cartItemRepository.findByUserId(userId));
        items.sort(Comparator.comparing(CartItem::getCreatedAt).reversed());
        Set<Long> productIds = new HashSet<>();
        items.forEach(i -> productIds.add(i.getProductId()));
        Map<Long, Product> products = new HashMap<>();
        productRepository.findAllById(productIds).forEach(p -> products.put(p.getId(), p));
        Set<Long> sellerIds = new HashSet<>();
        products.values().forEach(p -> sellerIds.add(p.getSellerId()));
        Map<Long, String> nicknames = new HashMap<>();
        userRepository.findAllById(sellerIds).forEach(u -> nicknames.put(u.getId(), u.getNickname()));
        Map<Long, String> covers = new HashMap<>();
        for (Long productId : productIds) {
            List<ProductImage> images = productImageRepository.findByProductIdOrderBySort(productId);
            if (!images.isEmpty()) {
                covers.put(productId, com.maimai.catalog.service.ProductImagePaths.publicUrl(images.get(0).getPath()));
            }
        }
        List<CartItemDto> result = new ArrayList<>();
        for (CartItem item : items) {
            Product product = products.get(item.getProductId());
            boolean invalid = product == null
                    || product.getStatus() != Product.Status.ON_SALE
                    || item.getQuantity() > product.getStockAvailable();
            result.add(new CartItemDto(
                    item.getId(), item.getProductId(),
                    product != null ? product.getTitle() : null,
                    product != null ? product.getPriceCents() : 0,
                    item.getQuantity(), item.getDeliveryMethod(),
                    product != null ? product.getStockAvailable() : null,
                    covers.get(item.getProductId()),
                    product != null ? product.getSellerId() : null,
                    product != null ? nicknames.get(product.getSellerId()) : null,
                    product != null ? product.getStatus().name() : null,
                    invalid));
        }
        return result;
    }

    public CartItemDto add(Long userId, Long productId, int quantity, String deliveryMethod) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (product.getStatus() != Product.Status.ON_SALE) {
            throw BizException.conflict("PRODUCT_NOT_ON_SALE", "商品当前不在售，无法加入购物车");
        }
        String method = deliveryMethod.trim().toUpperCase();
        if (!supports(product, method)) {
            throw BizException.badRequest("DELIVERY_METHOD_NOT_SUPPORTED", "该商品不支持所选交付方式");
        }
        Optional<CartItem> existing =
                cartItemRepository.findByUserIdAndProductIdAndDeliveryMethod(userId, productId, method);
        CartItem item;
        int newQuantity = quantity;
        if (existing.isPresent()) {
            item = existing.get();
            newQuantity = item.getQuantity() + quantity;
        } else {
            item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setDeliveryMethod(method);
        }
        if (newQuantity > product.getStockAvailable()) {
            throw BizException.conflict("INSUFFICIENT_STOCK", "数量超出可售库存");
        }
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
        return list(userId).stream()
                .filter(dto -> dto.id().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> BizException.conflict("CART_STATE", "购物车状态异常"));
    }

    public CartItemDto update(Long userId, Long cartItemId, int quantity) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> BizException.notFound("购物车项不存在"));
        SecurityUtils.requireOwner(item.getUserId());
        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (quantity > product.getStockAvailable()) {
            throw BizException.conflict("INSUFFICIENT_STOCK", "数量超出可售库存");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return list(userId).stream()
                .filter(dto -> dto.id().equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> BizException.conflict("CART_STATE", "购物车状态异常"));
    }

    public void delete(Long userId, Long cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> BizException.notFound("购物车项不存在"));
        SecurityUtils.requireOwner(item.getUserId());
        cartItemRepository.delete(item);
    }

    static boolean supports(Product product, String deliveryMethod) {
        if (product.getDeliveryMethods() == null) {
            return false;
        }
        for (String m : product.getDeliveryMethods().split(",")) {
            if (m.trim().equalsIgnoreCase(deliveryMethod)) {
                return true;
            }
        }
        return false;
    }
}
