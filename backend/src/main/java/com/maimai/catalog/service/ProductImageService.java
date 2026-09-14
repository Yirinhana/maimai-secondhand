package com.maimai.catalog.service;

import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.common.BizException;
import com.maimai.config.MaimaiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 商品图片：共享解码预算校验 JPG/PNG（≤5MB/16MP），缩至最长边1600并重编码为 JPEG
 * （天然去除 EXIF 等元数据），存储于 uploadDir/products/，DB 记录对外路径 /uploads/products/xxx.jpg。
 */
@Service
@Transactional
public class ProductImageService {

    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    /** 数据库 path 前缀与实际存储子目录的对应关系。 */
    private static final String URL_PREFIX = "/uploads/products/";

    private final ProductImageRepository productImageRepository;
    private final SellerProductService sellerProductService;
    private final Path storageDir;
    private final ProductRevisionService revisions;

    public ProductImageService(ProductImageRepository productImageRepository,
                               SellerProductService sellerProductService,
                               MaimaiProperties properties, ProductRevisionService revisions) {
        this.productImageRepository = productImageRepository;
        this.revisions = revisions;
        this.sellerProductService = sellerProductService;
        this.storageDir = Path.of(properties.getUploadDir()).resolve("products").toAbsolutePath().normalize();
    }

    public List<Long> upload(Long userId, Long productId, List<MultipartFile> files) {
        Product product = sellerProductService.ownedProduct(userId, productId);
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw BizException.badRequest("PRODUCT_IMAGES_REQUIRED", "请选择要上传的图片");
        }
        List<ProductImage> existing = productImageRepository.findByProductIdOrderBySort(productId);
        int total = existing.size() + files.size();
        if (total > SellerProductService.MAX_IMAGES) {
            throw BizException.badRequest("PRODUCT_IMAGES_TOO_MANY",
                    "商品图片最多 " + SellerProductService.MAX_IMAGES + " 张");
        }
        revisions.baseline(product,userId);
        List<Path> written = new ArrayList<>();
        try {
            List<Long> ids = new ArrayList<>();
            int sort = existing.isEmpty() ? 0 : existing.getLast().getSort() + 1;
            Files.createDirectories(storageDir);
            for (MultipartFile file : files) {
                byte[] encoded = com.maimai.common.SafeImageEncoder.encode(file,"PRODUCT");
                String name = UUID.randomUUID() + ".jpg";
                Path target = storageDir.resolve(name);
                Files.write(target, encoded, StandardOpenOption.CREATE_NEW);
                written.add(target);
                ProductImage image = new ProductImage();
                image.setProductId(productId);
                image.setPath(URL_PREFIX + name);
                image.setSort(sort++);
                productImageRepository.save(image);
                ids.add(image.getId());
            }
            sellerProductService.onImagesChanged(product);
            return ids;
        } catch (IOException ex) {
            cleanup(written);
            throw BizException.badRequest("PRODUCT_IMAGE_READ", "图片保存失败，请重试");
        } catch (RuntimeException ex) {
            cleanup(written);
            throw ex;
        }
    }

    public void delete(Long userId, Long productId, Long imageId) {
        Product product = sellerProductService.ownedProduct(userId, productId);
        ProductImage image = productImageRepository.findById(imageId)
                .filter(i -> i.getProductId().equals(productId))
                .orElseThrow(() -> BizException.notFound("图片不存在"));
        revisions.baseline(product,userId);
        productImageRepository.delete(image);
        // Keep the encoded file: immutable revisions and existing order images still reference it.
        sellerProductService.onImagesChanged(product);
    }

    /** 失败时清理本次已落盘文件，避免孤儿文件。 */
    private void cleanup(List<Path> written) {
        for (Path file : written) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                log.warn("商品图片清理失败 {}", file);
            }
        }
    }

}
