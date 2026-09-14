package com.maimai.catalog.controller;

import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.catalog.dto.CatalogDtos.ProductCreateRequest;
import com.maimai.catalog.dto.CatalogDtos.ProductUpdateRequest;
import com.maimai.catalog.dto.CatalogDtos.SellerProductItem;
import com.maimai.catalog.dto.CatalogDtos.StockAdjustRequest;
import com.maimai.catalog.service.ProductImageService;
import com.maimai.catalog.service.SellerProductService;
import com.maimai.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 卖家商品管理端点（需登录；卖家准入门槛在 service 内校验）。 */
@RestController
@RequestMapping("/api/v1/seller/products")
public class SellerProductController {

    private final SellerProductService sellerProductService;
    private final ProductImageService productImageService;

    public SellerProductController(SellerProductService sellerProductService,
                                   ProductImageService productImageService) {
        this.sellerProductService = sellerProductService;
        this.productImageService = productImageService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@RequestBody @Valid ProductCreateRequest request) {
        Long id = sellerProductService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id,
                                       @RequestBody @Valid ProductUpdateRequest request) {
        sellerProductService.update(SecurityUtils.currentUserId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<Void> adjustStock(@PathVariable Long id,
                                            @RequestBody @Valid StockAdjustRequest request) {
        sellerProductService.adjustStock(SecurityUtils.currentUserId(), id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submit(@PathVariable Long id) {
        sellerProductService.submit(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/off-shelf")
    public ResponseEntity<Void> offShelf(@PathVariable Long id) {
        sellerProductService.offShelf(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<Map<String, List<Long>>> uploadImages(@PathVariable Long id,
                                                                @RequestParam("files") List<MultipartFile> files) {
        List<Long> ids = productImageService.upload(SecurityUtils.currentUserId(), id, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ids", ids));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        productImageService.delete(SecurityUtils.currentUserId(), id, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResult<SellerProductItem> listMine(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        return sellerProductService.listMine(SecurityUtils.currentUserId(), status, page, size);
    }

    @GetMapping("/{id}")
    public com.maimai.catalog.dto.CatalogDtos.SellerProductDetail detailMine(@PathVariable Long id) {
        return sellerProductService.detailMine(SecurityUtils.currentUserId(), id);
    }
}
