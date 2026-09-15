package com.maimai.catalog.service;

import com.maimai.catalog.domain.Product;
import com.maimai.catalog.dto.CatalogDtos.*;
import com.maimai.catalog.repo.ProductRepository;
import com.maimai.common.BizException;
import com.maimai.common.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable content records are created in the product mutation's locked transaction. */
@Service
public class ProductRevisionService {
    private final JdbcTemplate jdbc;
    private final ProductRepository products;
    private final ProductAssembler assembler;
    private final JsonMapper json;
    public ProductRevisionService(JdbcTemplate jdbc,ProductRepository products,ProductAssembler assembler,JsonMapper json) {
        this.jdbc=jdbc;this.products=products;this.assembler=assembler;this.json=json;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void baseline(Product product,Long actorId) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM product_revisions WHERE product_id=?",Long.class,product.getId())==0)record(product,actorId,"BASELINE");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Product product,Long actorId,String action) {
        int version=jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM product_revisions WHERE product_id=?",Integer.class,product.getId());
        Content content=new Content(product.getTitle(),product.getCategoryId(),product.getDescription(),product.getItemCondition().name(),product.getDefects(),product.getPriceCents(),product.getRegion(),ProductAssembler.splitDeliveryMethods(product.getDeliveryMethods()),product.getFreightCents(),product.getReturnPromise(),ProductShipping.split(product.getShippingProvinces()),product.getLatitude(),product.getLongitude(),assembler.images(product.getId()),product.getStatus().name(),product.getReviewReason(),product.getExperienceSource(),product.getSupplyNote());
        jdbc.update("INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES(?,?,?,?,?)",product.getId(),version,action,actorId,json.writeValueAsString(content));
    }
    @Transactional(readOnly=true)
    public PageResult<Revision> list(long productId,int page,int size,boolean admin) {
        if(page<0||page>100000||size<1||size>50)throw BizException.badRequest("PAGE_INVALID","分页参数不合法");
        var product=products.findById(productId).orElseThrow(()->BizException.notFound("商品不存在"));
        if(admin) {
            var roles=SecurityUtils.current().roles();
            if(!roles.contains("SUPER_ADMIN")&&!roles.contains("OPERATOR"))throw BizException.forbidden("需要商品审核权限");
        } else if(!SecurityUtils.currentUserId().equals(product.getSellerId()))throw BizException.notFound("商品不存在");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM product_revisions WHERE product_id=?",Long.class,productId);
        var content=jdbc.query("SELECT version,action,content,created_at FROM product_revisions WHERE product_id=? ORDER BY version DESC LIMIT ? OFFSET ?",(rs,row)->new Revision(rs.getInt("version"),actionLabel(rs.getString("action")),rs.getTimestamp("created_at").toInstant(),display(json.readValue(rs.getString("content"),Content.class))),productId,size,(long)page*size);
        return new PageResult<>(content,total,(int)((total+size-1)/size),page,size);
    }
    private DisplayContent display(Content c) {
        String category=jdbc.query("SELECT name FROM categories WHERE id=?",(rs,n)->rs.getString(1),c.categoryId()).stream().findFirst().orElse("原分类已调整");
        return new DisplayContent(c.title(),category,c.description(),c.condition(),c.defects(),c.priceCents(),c.region(),c.deliveryMethods(),c.freightCents(),c.returnPromise(),c.shippingProvinces(),
                c.images()==null?List.of():c.images().stream().map(i->ProductImagePaths.publicUrl(i.path())).filter(Objects::nonNull).map(DisplayImage::new).toList(),c.status(),c.reviewReason());
    }
    private String actionLabel(String action) {
        return switch(action) {
            case "CREATE" -> "创建商品";
            case "BASELINE" -> "首次记录";
            case "UPDATE" -> "修改商品信息";
            case "REVIEW" -> "审核商品";
            case "SUBMIT", "SUBMIT_REVIEW" -> "提交审核";
            case "OFF_SHELF" -> "下架商品";
            case "IMAGE_UPLOAD" -> "添加商品图片";
            case "IMAGE_DELETE" -> "移除商品图片";
            case "IMAGES_CHANGED" -> "更新商品图片";
            case "APPROVE" -> "审核通过";
            case "REJECT" -> "审核未通过";
            default -> "保存商品信息";
        };
    }
    // API 仅返回业务展示内容；原始审计记录留在数据库，不向页面暴露内部编号和来源标记。
    public record Revision(int version,String actionLabel,Instant createdAt,DisplayContent content) {}
    public record DisplayImage(String path) {}
    public record DisplayContent(String title,String categoryName,String description,String condition,String defects,long priceCents,String region,List<String> deliveryMethods,long freightCents,String returnPromise,List<String> shippingProvinces,List<DisplayImage> images,String status,String reviewReason) {}
    public record Content(String title,long categoryId,String description,String condition,String defects,long priceCents,String region,List<String> deliveryMethods,long freightCents,String returnPromise,List<String> shippingProvinces,BigDecimal latitude,BigDecimal longitude,List<ImageItem> images,String status,String reviewReason,String experienceSource,String supplyNote) {}
}
