package com.maimai.catalog.service;
import com.maimai.common.*;
import com.maimai.config.MaimaiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
@Service
public class DraftImageAttachmentService {
    private final JdbcTemplate jdbc;
    private final SellerProductService products;
    private final PersonalMediaService media;
    private final Path dir;
    private final ProductRevisionService revisions;
    public DraftImageAttachmentService(JdbcTemplate jdbc,SellerProductService products,PersonalMediaService media,MaimaiProperties properties,ProductRevisionService revisions){this.jdbc=jdbc;this.products=products;this.media=media;this.dir=Path.of(properties.getUploadDir()).resolve("products").toAbsolutePath().normalize();this.revisions=revisions;}
    @Transactional
    public void attach(long user,long productId,List<String> ids){
        if(ids==null||ids.size()>9||new HashSet<>(ids).size()!=ids.size())throw BizException.badRequest("DRAFT_IMAGES","草稿图片最多9张且不能重复");
        var product=products.ownedProduct(user,productId);
        var fresh=ids.stream().filter(id->jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id=? AND source_media_id=?",Long.class,productId,id)==0).toList();
        if(fresh.isEmpty())return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id=?",Long.class,productId)+fresh.size()>9)throw BizException.badRequest("DRAFT_IMAGES","合计图片超过9张，请先移除部分图片");
        revisions.baseline(product,user);
        int sort=jdbc.queryForObject("SELECT COALESCE(MAX(sort),-1)+1 FROM product_images WHERE product_id=?",Integer.class,productId);
        for(String id:fresh){
            byte[] bytes=media.owned(user,id,"DRAFT");
            String name=UUID.nameUUIDFromBytes((productId+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8))+".jpg";
            try{Files.createDirectories(dir);Files.write(dir.resolve(name),bytes);}catch(IOException e){throw BizException.badRequest("DRAFT_IMAGE_WRITE","商品图片保存失败，草稿仍保留，可重试");}
            PersonalMediaService.rollbackFile(dir.resolve(name));
            jdbc.update("INSERT INTO product_images(product_id,path,sort,source_media_id) VALUES(?,?,?,?)",productId,"/uploads/products/"+name,sort++,id);
        }
        products.onImagesChanged(product);
    }
}
