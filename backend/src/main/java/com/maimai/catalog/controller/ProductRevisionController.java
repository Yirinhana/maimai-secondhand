package com.maimai.catalog.controller;

import com.maimai.catalog.dto.CatalogDtos.PageResult;
import com.maimai.catalog.service.ProductRevisionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ProductRevisionController {
    private final ProductRevisionService revisions;
    public ProductRevisionController(ProductRevisionService revisions){this.revisions=revisions;}
    @GetMapping("/seller/products/{id}/revisions")
    public PageResult<ProductRevisionService.Revision> mine(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return revisions.list(id,page,size,false);}
    @GetMapping("/admin/products/{id}/revisions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
    public PageResult<ProductRevisionService.Revision> admin(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return revisions.list(id,page,size,true);}
}
