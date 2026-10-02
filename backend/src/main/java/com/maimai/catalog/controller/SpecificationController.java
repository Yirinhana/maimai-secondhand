package com.maimai.catalog.controller;
import com.maimai.catalog.service.ProductSpecifications;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
public class SpecificationController {
    private final ProductSpecifications service;
    public SpecificationController(ProductSpecifications service){this.service=service;}
    @GetMapping("/api/v1/categories/{id}/specifications")
    public List<ProductSpecifications.Field> fields(@PathVariable long id){return service.fields(id);}
}
