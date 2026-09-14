package com.maimai.catalog.controller;

import com.maimai.catalog.dto.CatalogDtos.CategoryNode;
import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.catalog.dto.CatalogDtos.ProductDetail;
import com.maimai.catalog.dto.CatalogDtos.ProductSummary;
import com.maimai.catalog.dto.CatalogDtos.SellerProfile;
import com.maimai.catalog.service.CategoryService;
import com.maimai.catalog.service.ProductQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 公开商品浏览端点（SecurityConfig 已对 GET 放行）。 */
@RestController
@RequestMapping("/api/v1")
public class PublicCatalogController {

    private final CategoryService categoryService;
    private final ProductQueryService productQueryService;

    public PublicCatalogController(CategoryService categoryService, ProductQueryService productQueryService) {
        this.categoryService = categoryService;
        this.productQueryService = productQueryService;
    }

    @GetMapping("/categories")
    public List<CategoryNode> categories() {
        return categoryService.tree();
    }

    @GetMapping("/shipping-provinces")
    public List<String> shippingProvinces(){return com.maimai.catalog.service.ProductShipping.PROVINCES;}

    @GetMapping("/products")
    public PageResult<ProductSummary> search(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) String condition,
                                             @RequestParam(required = false) Long minPriceCents,
                                             @RequestParam(required = false) Long maxPriceCents,
                                             @RequestParam(required = false) String region,
                                             @RequestParam(required = false) String deliveryMethod,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) Double originLatitude,
                                             @RequestParam(required = false) Double originLongitude) {
        return productQueryService.search(keyword, categoryId, condition, minPriceCents, maxPriceCents,
                region, deliveryMethod, sort, page, size,originLatitude,originLongitude);
    }

    @GetMapping("/products/{id}")
    public ProductDetail detail(@PathVariable Long id) {
        return productQueryService.detail(id);
    }

    @GetMapping("/sellers/{id}")
    public SellerProfile sellerProfile(@PathVariable Long id) {
        return productQueryService.sellerProfile(id);
    }

    @GetMapping("/sellers/{id}/products")
    public PageResult<ProductSummary> sellerProducts(@PathVariable Long id,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return productQueryService.sellerProducts(id, page, size);
    }
}
