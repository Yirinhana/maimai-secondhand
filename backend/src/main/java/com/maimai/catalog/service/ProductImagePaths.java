package com.maimai.catalog.service;

/** Supports existing relative seed paths and canonical uploaded product URLs exactly once. */
public final class ProductImagePaths {
    private ProductImagePaths() {}
    public static String publicUrl(String path) {
        if(path==null || path.isBlank())return null;
        if(path.startsWith("products/"))return "/uploads/"+path;
        return path.startsWith("/uploads/products/") ? path : null;
    }
}
