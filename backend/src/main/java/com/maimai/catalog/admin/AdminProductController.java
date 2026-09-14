package com.maimai.catalog.admin;

import com.maimai.catalog.admin.AdminProductDtos.AdminProductItem;
import com.maimai.catalog.admin.AdminProductDtos.ReviewRequest;
import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台商品审核端点（SecurityConfig 已限制 /api/v1/admin/** 为 OPERATOR/SUPPORT/SUPER_ADMIN）。 */
@RestController
@RequestMapping("/api/v1/admin/products")
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @GetMapping
    public PageResult<AdminProductItem> listPending(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return adminProductService.listPending(status, page, size);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<Void> review(@PathVariable Long id,
                                       @RequestBody @Valid ReviewRequest request) {
        adminProductService.review(SecurityUtils.currentUserId(), id, request.approve(), request.reason());
        return ResponseEntity.noContent().build();
    }
}
