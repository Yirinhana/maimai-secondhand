package com.maimai.admin.controller;

import com.maimai.catalog.dto.CatalogDtos.SellerProductDetail;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.catalog.service.SellerProductService;
import com.maimai.common.BizException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductDetailController {
    private final ProductRepository products;
    private final SellerProductService service;
    public AdminProductDetailController(ProductRepository products, SellerProductService service) {
        this.products = products; this.service = service;
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    public SellerProductDetail detail(@PathVariable Long id) {
        var product = products.findById(id).orElseThrow(() -> BizException.notFound("商品不存在"));
        return service.detailMine(product.getSellerId(), id);
    }
}
