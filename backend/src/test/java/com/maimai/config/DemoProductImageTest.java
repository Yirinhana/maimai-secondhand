package com.maimai.config;

import com.maimai.catalog.domain.Category;
import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.repo.CategoryRepository;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.identity.domain.User;
import com.maimai.identity.repo.AddressRepository;
import com.maimai.identity.repo.SellerApplicationRepository;
import com.maimai.identity.repo.UserRepository;
import com.maimai.identity.repo.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemoProductImageTest {
    @TempDir Path uploadDir;

    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final SellerApplicationRepository applications = mock(SellerApplicationRepository.class);
    private final AddressRepository addresses = mock(AddressRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);

    private DataSeeder seeder() {
        MaimaiProperties properties = new MaimaiProperties();
        properties.setUploadDir(uploadDir.toString());
        return new DataSeeder(users, roles, applications, addresses, categories,
                products, images, passwords, properties);
    }

    @Test
    void copiesCorrectDecodableAssetsEvenWhenGeneratedProductIdsDoNotStartAtOne() throws Exception {
        AtomicLong userIds = new AtomicLong(101);
        AtomicLong productIds = new AtomicLong(41);
        List<Product> savedProducts = new ArrayList<>();
        List<ProductImage> savedImages = new ArrayList<>();
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(userIds.getAndIncrement());
            return user;
        });
        when(passwords.encode(any())).thenReturn("test-only-hash");
        when(categories.findByStatusOrderBySort(Category.Status.ACTIVE)).thenReturn(
                List.of("手机", "影音家电", "图书教材", "数码电子", "服饰鞋包", "运动户外")
                        .stream().map(name -> {
                            Category category = new Category();
                            category.setId((long) name.hashCode());
                            category.setName(name);
                            return category;
                        }).toList());
        when(products.save(any(Product.class))).thenAnswer(call -> {
            Product product = call.getArgument(0);
            product.setId(productIds.getAndAdd(7));
            savedProducts.add(product);
            return product;
        });
        when(images.save(any(ProductImage.class))).thenAnswer(call -> {
            ProductImage image = call.getArgument(0);
            savedImages.add(image);
            return image;
        });

        seeder().run(null);

        Map<String, String> expectedAssets = Map.of(
                "iPhone 12 128GB 蓝色 自用一手", "iphone-blue.jpg",
                "索尼 WH-1000XM4 降噪耳机", "headphones-charcoal.jpg",
                "Java 核心技术 卷I（第11版）", "java-textbook.jpg",
                "优衣库羊毛混纺大衣 M 码", "wool-coat.jpg",
                "尤尼克斯羽毛球拍 天斧77", "badminton-racket.jpg");
        assertThat(savedProducts).hasSize(6);
        assertThat(savedImages).hasSize(6);
        for (Product product : savedProducts) {
            ProductImage image = savedImages.stream()
                    .filter(item -> item.getProductId().equals(product.getId())).findFirst().orElseThrow();
            String fileName = "seed-" + product.getId() + ".jpg";
            assertThat(image.getPath()).isEqualTo("/uploads/products/" + fileName);
            assertThat(image.getSort()).isZero();
            byte[] stored = Files.readAllBytes(uploadDir.resolve("products").resolve(fileName));
            var decoded = ImageIO.read(new ByteArrayInputStream(stored));
            assertThat(decoded).as(product.getTitle() + " JPEG decodes").isNotNull();
            assertThat(stored).startsWith((byte) 0xff, (byte) 0xd8);
            try {
                String asset = expectedAssets.get(product.getTitle());
                if (asset != null) {
                    try (InputStream input = getClass().getResourceAsStream("/demo/products/" + asset)) {
                        assertThat(input).as("Packaged asset: " + asset).isNotNull();
                        assertThat(stored).as(product.getTitle() + " matches its explicit resource")
                                .isEqualTo(input.readAllBytes());
                    }
                    assertThat(decoded.getWidth()).isGreaterThan(0);
                    assertThat(decoded.getHeight()).isGreaterThan(0);
                    assertThat(product.getStatus()).isEqualTo(Product.Status.ON_SALE);
                } else {
                    assertThat(product.getTitle()).isEqualTo("Switch OLED 日版 九九新");
                    assertThat(product.getStatus()).isEqualTo(Product.Status.PENDING_REVIEW);
                    assertThat(decoded.getWidth()).isEqualTo(600);
                    assertThat(decoded.getHeight()).isEqualTo(600);
                }
            } finally {
                decoded.flush();
            }
        }
        try (var files = Files.list(uploadDir.resolve("products"))) {
            assertThat(files.count()).isEqualTo(6);
        }
    }

    @Test
    void existingDatabaseSkipsSeedingAndPreservesItsImageBytes() throws Exception {
        Path existing = uploadDir.resolve("products/seed-1.jpg");
        Files.createDirectories(existing.getParent());
        byte[] original = { 3, 7, 1, 9 };
        Files.write(existing, original);
        when(users.count()).thenReturn(1L);

        seeder().run(null);

        assertThat(Files.readAllBytes(existing)).isEqualTo(original);
        verifyNoInteractions(roles, applications, addresses, categories, products, images, passwords);
    }
}
