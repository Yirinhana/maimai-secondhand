package com.maimai.admin.controller;

import com.maimai.admin.dto.GovernanceDtos.*;
import com.maimai.admin.service.AdminCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService categories;
    public AdminCategoryController(AdminCategoryService categories) {this.categories=categories;}
    @GetMapping public List<CategoryItem> list() {return categories.list();}
    @PostMapping public CategoryItem create(@RequestBody @Valid CategoryWrite request) {return categories.save(null,request);}
    @PutMapping("/{id}") public CategoryItem update(@PathVariable long id,@RequestBody @Valid CategoryWrite request) {return categories.save(id,request);}
}
