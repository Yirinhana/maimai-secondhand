package com.maimai.config;

import com.maimai.catalog.domain.Category;
import com.maimai.catalog.domain.Product;
import com.maimai.catalog.domain.ProductImage;
import com.maimai.catalog.repo.CategoryRepository;
import com.maimai.catalog.repo.ProductImageRepository;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.identity.domain.Address;
import com.maimai.identity.domain.SellerApplication;
import com.maimai.identity.domain.User;
import com.maimai.identity.domain.UserRole;
import com.maimai.identity.repo.AddressRepository;
import com.maimai.identity.repo.SellerApplicationRepository;
import com.maimai.identity.repo.UserRepository;
import com.maimai.identity.repo.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 本地开发种子数据（仅 local profile 启用）。
 * 仅当 users 表为空时执行；种子账号密码统一 Maimai#2026，仅用于本地开发，严禁用于任何真实环境。
 * 商品主图由程序生成 600×600 纯色 JPEG（画出商品标题文字）写入上传目录，不依赖任何外部资源。
 */
@Profile("local")
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_PASSWORD = "Maimai#2026";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final AddressRepository addressRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final PasswordEncoder passwordEncoder;
    private final MaimaiProperties properties;

    public DataSeeder(UserRepository userRepository,
                      UserRoleRepository userRoleRepository,
                      SellerApplicationRepository sellerApplicationRepository,
                      AddressRepository addressRepository,
                      CategoryRepository categoryRepository,
                      ProductRepository productRepository,
                      ProductImageRepository productImageRepository,
                      PasswordEncoder passwordEncoder,
                      MaimaiProperties properties) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.sellerApplicationRepository = sellerApplicationRepository;
        this.addressRepository = addressRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(SEED_PASSWORD);
        User admin = createUser("admin@maimai.local", "平台管理员", hash,
                UserRole.Role.SUPER_ADMIN, UserRole.Role.USER);
        createUser("operator@maimai.local", "运营小助手", hash,
                UserRole.Role.OPERATOR, UserRole.Role.USER);
        createUser("support@maimai.local", "客服小帮手", hash,
                UserRole.Role.SUPPORT, UserRole.Role.USER);
        User seller = createUser("seller@maimai.local", "数码老王", hash,
                UserRole.Role.USER, UserRole.Role.SELLER);
        User seller2 = createUser("seller2@maimai.local", "衣橱小陈", hash,
                UserRole.Role.USER, UserRole.Role.SELLER);
        User buyer = createUser("buyer@maimai.local", "爱淘的麦同学", hash,
                UserRole.Role.USER);

        approveSeller(seller, admin, "在校学生，转让闲置数码与教材");
        approveSeller(seller2, admin, "换季衣橱整理，出闲置衣物与运动装备");

        Address address = new Address();
        address.setUserId(buyer.getId());
        address.setReceiver("麦同学");
        address.setPhone("13800000000");
        address.setRegion("四川省成都市武侯区");
        address.setDetail("望江校区东园 3 栋 502");
        address.setDefault(true);
        addressRepository.save(address);

        Map<String, Category> categories = categoryRepository
                .findByStatusOrderBySort(Category.Status.ACTIVE).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity()));

        seedProduct(seller, categories.get("手机"), "iPhone 12 128GB 蓝色 自用一手",
                "自用两年，全程戴壳贴膜，电池健康 86%，配件齐全。",
                Product.Condition.LIKE_NEW, "边框一处轻微掉漆", 219900, 1,
                "四川省成都市", "EXPRESS,MEETUP", 0, "面交当场验机，快递签收后 48 小时内可退",
                Product.Status.ON_SALE, new Color(52, 96, 168));
        seedProduct(seller, categories.get("影音家电"), "索尼 WH-1000XM4 降噪耳机",
                "功能完好，降噪正常，耳罩略有使用痕迹，附收纳盒。",
                Product.Condition.GOOD, "耳罩轻微褶皱", 89900, 1,
                "四川省成都市", "EXPRESS", 1200, "签收后 48 小时内可退",
                Product.Status.ON_SALE, new Color(70, 70, 78));
        seedProduct(seller, categories.get("图书教材"), "Java 核心技术 卷I（第11版）",
                "课程用书，内页少量笔记，无缺页。",
                Product.Condition.GOOD, "封面轻微磨损", 4500, 2,
                "四川省成都市", "EXPRESS,MEETUP", 600, "图书售出不退",
                Product.Status.ON_SALE, new Color(126, 84, 40));
        seedProduct(seller, categories.get("数码电子"), "Switch OLED 日版 九九新",
                "仅试玩，箱说全，待平台审核上架。",
                Product.Condition.LIKE_NEW, null, 189900, 1,
                "四川省成都市", "MEETUP", 0, "面交当场验机",
                Product.Status.PENDING_REVIEW, new Color(160, 60, 60));

        seedProduct(seller2, categories.get("服饰鞋包"), "优衣库羊毛混纺大衣 M 码",
                "去年购入，穿着个位数，已干洗。",
                Product.Condition.LIKE_NEW, null, 15900, 1,
                "重庆市", "EXPRESS", 1000, "签收后 48 小时内可退",
                Product.Status.ON_SALE, new Color(140, 100, 130));
        seedProduct(seller2, categories.get("运动户外"), "尤尼克斯羽毛球拍 天斧77",
                "已拉 26 磅线，拍框一处磕碰不影响打感。",
                Product.Condition.GOOD, "拍框一处磕碰", 26000, 1,
                "重庆市", "EXPRESS,MEETUP", 800, "当面验拍",
                Product.Status.ON_SALE, new Color(60, 130, 90));

        log.info("开发种子数据已初始化：6 个账号（admin/operator/support/seller/seller2/buyer@maimai.local），"
                + "6 个商品（5 个在售，1 个待审核）");
    }

    private User createUser(String email, String nickname, String passwordHash, UserRole.Role... roles) {
        User user = new User();
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPasswordHash(passwordHash);
        userRepository.save(user);
        for (UserRole.Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRole(role);
            userRoleRepository.save(userRole);
        }
        return user;
    }

    private void approveSeller(User user, User admin, String intro) {
        SellerApplication application = new SellerApplication();
        application.setUserId(user.getId());
        application.setStatus(SellerApplication.Status.APPROVED);
        application.setChannelStatus(SellerApplication.ChannelStatus.QUALIFIED);
        application.setIntro(intro);
        application.setReason("开发种子数据直接通过");
        application.setReviewedBy(admin.getId());
        application.setReviewedAt(Instant.now());
        sellerApplicationRepository.save(application);
    }

    private void seedProduct(User seller, Category category, String title, String description,
                             Product.Condition condition, String defects, long priceCents, int stock,
                             String region, String deliveryMethods, long freightCents,
                             String returnPromise, Product.Status status, Color imageColor) {
        Product product = new Product();
        product.setSellerId(seller.getId());
        product.setCategoryId(category.getId());
        product.setTitle(title);
        product.setDescription(description);
        product.setItemCondition(condition);
        product.setDefects(defects);
        product.setPriceCents(priceCents);
        product.setStockAvailable(stock);
        product.setRegion(region);
        product.setDeliveryMethods(deliveryMethods);
        product.setFreightCents(freightCents);
        product.setReturnPromise(returnPromise);
        product.setStatus(status);
        productRepository.save(product);
        seedImage(product, imageColor);
    }

    /** 生成 600×600 纯色 JPEG 主图（画出商品标题文字）并登记 product_images。 */
    private void seedImage(Product product, Color color) {
        String fileName = "seed-" + product.getId() + ".jpg";
        try {
            Path dir = Path.of(properties.getUploadDir(), "products").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(color);
                g.fillRect(0, 0, 600, 600);
                g.setColor(Color.WHITE);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
                drawWrappedTitle(g, product.getTitle());
            } finally {
                g.dispose();
            }
            ImageIO.write(image, "jpeg", dir.resolve(fileName).toFile());
            image.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("种子商品图片生成失败: " + fileName, ex);
        }
        ProductImage productImage = new ProductImage();
        productImage.setProductId(product.getId());
        productImage.setPath("/uploads/products/" + fileName);
        productImage.setSort(0);
        productImageRepository.save(productImage);
    }

    /** 标题按每行 10 个字符换行，居中绘制。 */
    private void drawWrappedTitle(Graphics2D g, String title) {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < title.length(); i += 10) {
            lines.add(title.substring(i, Math.min(i + 10, title.length())));
        }
        int lineHeight = g.getFontMetrics().getHeight();
        int startY = 300 - (lines.size() - 1) * lineHeight / 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int width = g.getFontMetrics().stringWidth(line);
            g.drawString(line, (600 - width) / 2, startY + i * lineHeight);
        }
    }
}
