package com.maimai.catalog.controller;

import com.maimai.catalog.service.ProductThumbnailService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import java.io.IOException;
import java.time.Duration;

@RestController
public class ProductThumbnailController {
    private final ProductThumbnailService service;
    public ProductThumbnailController(ProductThumbnailService service) { this.service = service; }
    @GetMapping("/uploads/thumbnails/products/{width}/{name}")
    public ResponseEntity<Resource> get(@PathVariable int width, @PathVariable String name, WebRequest request) throws IOException {
        var image = service.get(width,name);
        var response = ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .eTag(image.etag()).lastModified(image.lastModified());
        if (request.checkNotModified(image.etag(), image.lastModified())) return ResponseEntity.status(304).eTag(image.etag()).build();
        return response.body(new FileSystemResource(image.path()));
    }
}
