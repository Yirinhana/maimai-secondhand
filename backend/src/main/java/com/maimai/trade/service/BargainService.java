package com.maimai.trade.service;

import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import com.maimai.identity.repo.UserRepository;
import com.maimai.trade.domain.BargainOffer;
import com.maimai.trade.dto.TradeDtos.BargainDto;
import com.maimai.trade.repo.BargainOfferRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 议价：买家出价、卖家接受/还价/拒绝、买家确认还价。报价 24 小时有效，惰性过期。 */
@Service
@Transactional
public class BargainService {

    private static final Duration OFFER_TTL = Duration.ofHours(24);

    private final BargainOfferRepository bargainOfferRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    public BargainService(BargainOfferRepository bargainOfferRepository,
                          ProductRepository productRepository,
                          ProductImageRepository productImageRepository,
                          UserRepository userRepository) {
        this.bargainOfferRepository = bargainOfferRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.userRepository = userRepository;
    }

    public BargainDto create(Long buyerId, Long productId, int quantity, long offerPriceCents) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (product.getStatus() != Product.Status.ON_SALE) {
            throw BizException.conflict("PRODUCT_NOT_ON_SALE", "商品当前不在售，无法议价");
        }
        if (product.getSellerId().equals(buyerId)) {
            throw BizException.badRequest("SELF_BARGAIN", "不能对自己的商品发起议价");
        }
        if (quantity > product.getStockAvailable()) {
            throw BizException.conflict("INSUFFICIENT_STOCK", "数量超出可售库存");
        }
        BargainOffer offer = new BargainOffer();
        offer.setProductId(productId);
        offer.setBuyerId(buyerId);
        offer.setQuantity(quantity);
        offer.setOfferPriceCents(offerPriceCents);
        offer.setStatus(BargainOffer.Status.PENDING);
        offer.setExpiresAt(Instant.now().plus(OFFER_TTL));
        bargainOfferRepository.save(offer);
        return toDtos(List.of(offer)).get(0);
    }

    public List<BargainDto> listBuyer(Long buyerId) {
        List<BargainOffer> offers = bargainOfferRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
        return toDtos(offers);
    }

    public List<BargainDto> listSeller(Long sellerId) {
        List<Long> productIds = productRepository
                .findBySellerIdOrderByCreatedAtDesc(sellerId, Pageable.unpaged())
                .map(Product::getId)
                .getContent();
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<BargainOffer> offers = new ArrayList<>();
        for (Long productId : productIds) {
            offers.addAll(bargainOfferRepository.findByProductIdOrderByCreatedAtDesc(productId));
        }
        offers.sort(Comparator.comparing(BargainOffer::getCreatedAt).reversed());
        return toDtos(offers);
    }

    public BargainDto accept(Long sellerId, Long offerId) {
        BargainOffer offer = requireSellerPendingOffer(sellerId, offerId);
        offer.setStatus(BargainOffer.Status.CONFIRMED);
        bargainOfferRepository.save(offer);
        return toDtos(List.of(offer)).get(0);
    }

    public BargainDto counter(Long sellerId, Long offerId, long counterPriceCents) {
        BargainOffer offer = requireSellerPendingOffer(sellerId, offerId);
        offer.setCounterPriceCents(counterPriceCents);
        offer.setStatus(BargainOffer.Status.COUNTERED);
        bargainOfferRepository.save(offer);
        return toDtos(List.of(offer)).get(0);
    }

    public BargainDto reject(Long sellerId, Long offerId, String reason) {
        // reason 暂无落库字段，仅作为操作语义保留
        BargainOffer offer = requireSellerPendingOffer(sellerId, offerId);
        offer.setStatus(BargainOffer.Status.REJECTED);
        bargainOfferRepository.save(offer);
        return toDtos(List.of(offer)).get(0);
    }

    public BargainDto confirm(Long buyerId, Long offerId) {
        BargainOffer offer = bargainOfferRepository.lockById(offerId)
                .orElseThrow(() -> BizException.notFound("议价单不存在"));
        SecurityUtils.requireOwner(offer.getBuyerId());
        lazyExpire(offer);
        if (offer.getStatus() != BargainOffer.Status.COUNTERED) {
            throw BizException.conflict("BARGAIN_STATE", "仅还价中的议价单可以确认");
        }
        offer.setStatus(BargainOffer.Status.CONFIRMED);
        bargainOfferRepository.save(offer);
        return toDtos(List.of(offer)).get(0);
    }

    private BargainOffer requireSellerPendingOffer(Long sellerId, Long offerId) {
        BargainOffer offer = bargainOfferRepository.lockById(offerId)
                .orElseThrow(() -> BizException.notFound("议价单不存在"));
        Product product = productRepository.findById(offer.getProductId())
                .orElseThrow(() -> BizException.notFound("商品不存在"));
        if (!product.getSellerId().equals(sellerId)) {
            throw BizException.forbidden("仅商品卖家可操作该议价单");
        }
        lazyExpire(offer);
        if (offer.getStatus() != BargainOffer.Status.PENDING) {
            throw BizException.conflict("BARGAIN_STATE", "议价单当前状态不允许该操作");
        }
        return offer;
    }

    /** 惰性过期：读取/使用时若已过期且仍为 PENDING/COUNTERED，置 EXPIRED。 */
    private void lazyExpire(BargainOffer offer) {
        if ((offer.getStatus() == BargainOffer.Status.PENDING
                || offer.getStatus() == BargainOffer.Status.COUNTERED)
                && Instant.now().isAfter(offer.getExpiresAt())) {
            throw BizException.conflict("BARGAIN_EXPIRED", "议价单已过期");
        }
    }

    private List<BargainDto> toDtos(List<BargainOffer> offers) {
        Set<Long> productIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (BargainOffer offer : offers) {
            productIds.add(offer.getProductId());
            userIds.add(offer.getBuyerId());
        }
        Map<Long, Product> products = new HashMap<>();
        productRepository.findAllById(productIds).forEach(p -> products.put(p.getId(), p));
        products.values().forEach(p -> userIds.add(p.getSellerId()));
        Map<Long, String> nicknames = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> nicknames.put(u.getId(), u.getNickname()));
        Map<Long, String> covers = new HashMap<>();
        for (Long productId : productIds) {
            List<ProductImage> images = productImageRepository.findByProductIdOrderBySort(productId);
            if (!images.isEmpty()) {
                covers.put(productId, images.get(0).getPath());
            }
        }
        List<BargainDto> result = new ArrayList<>();
        for (BargainOffer offer : offers) {
            Product product = products.get(offer.getProductId());
            Long sellerId = product != null ? product.getSellerId() : null;
            result.add(new BargainDto(
                    offer.getId(), offer.getProductId(),
                    product != null ? product.getTitle() : null,
                    covers.get(offer.getProductId()),
                    sellerId, nicknames.get(sellerId),
                    offer.getBuyerId(), nicknames.get(offer.getBuyerId()),
                    offer.getQuantity(), offer.getOfferPriceCents(), offer.getCounterPriceCents(),
                    ((offer.getStatus() == BargainOffer.Status.PENDING || offer.getStatus() == BargainOffer.Status.COUNTERED
                            || offer.getStatus() == BargainOffer.Status.CONFIRMED) && !offer.getExpiresAt().isAfter(Instant.now()))
                            ? "EXPIRED" : offer.getStatus().name(), offer.getExpiresAt(), offer.getUsedOrderId(),
                    offer.getCreatedAt()));
        }
        return result;
    }
}
