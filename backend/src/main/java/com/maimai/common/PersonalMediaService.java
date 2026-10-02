package com.maimai.common;

import com.maimai.config.MaimaiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Not under /uploads: draft photos remain private until explicitly attached to a listing/review. */
@Service
public class PersonalMediaService {
    private final JdbcTemplate jdbc;
    private final SimpleRateLimiter limiter;
    private final Path dir;
    public PersonalMediaService(JdbcTemplate jdbc,SimpleRateLimiter limiter,MaimaiProperties properties){
        this.jdbc=jdbc;this.limiter=limiter;
        this.dir=Path.of(properties.getUploadDir()).toAbsolutePath().normalize().resolveSibling("personal-media");
    }
    public record Media(String id,String url){}
    @Transactional
    public Media upload(long user,String purpose,MultipartFile file){
        if(!Set.of("DRAFT","REVIEW").contains(purpose))throw BizException.badRequest("MEDIA_PURPOSE","图片用途无效");
        limiter.require("personal-media:"+user,20,300,"图片上传频繁，请稍后重试");
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM personal_media WHERE owner_id=? AND created_at>UTC_TIMESTAMP()-INTERVAL 1 DAY",Long.class,user)>=100)throw BizException.tooMany("今日上传图片已达上限，请明日再试");
        String id=UUID.randomUUID().toString(),filename=id+".jpg";
        try {
            byte[] bytes=SafeImageEncoder.encode(file,"PERSONAL");Files.createDirectories(dir);Files.write(dir.resolve(filename),bytes,StandardOpenOption.CREATE_NEW);
            rollbackFile(dir.resolve(filename));
            jdbc.update("INSERT INTO personal_media(id,owner_id,purpose,filename) VALUES(?,?,?,?)",id,user,purpose,filename);
            return new Media(id,"/api/v1/me/media/"+id);
        }catch(IOException e){throw BizException.badRequest("MEDIA_WRITE","图片保存失败，请重试");}
    }
    public byte[] owned(long user,String id,String purpose){
        var rows=jdbc.query("SELECT filename FROM personal_media WHERE id=? AND owner_id=? AND purpose=?",(r,n)->r.getString(1),id,user,purpose);
        if(rows.isEmpty())throw BizException.notFound("图片不存在");return bytes(rows.getFirst());
    }
    public byte[] mine(long user,String id){
        var rows=jdbc.query("SELECT filename FROM personal_media WHERE id=? AND owner_id=?",(r,n)->r.getString(1),id,user);
        if(rows.isEmpty())throw BizException.notFound("图片不存在");return bytes(rows.getFirst());
    }
    public byte[] review(long rating,String id){
        var rows=jdbc.query("SELECT m.filename FROM rating_images i JOIN personal_media m ON m.id=i.media_id JOIN community_order_ratings r ON r.id=i.rating_id JOIN orders o ON o.id=r.order_id JOIN users u ON u.id=r.rater_id WHERE i.rating_id=? AND i.media_id=? AND r.is_hidden=0 AND u.status='ACTIVE' AND ((r.rater_id=o.buyer_id AND r.ratee_id=o.seller_id) OR (r.rater_id=o.seller_id AND r.ratee_id=o.buyer_id)) AND r.rater_id<>r.ratee_id",(r,n)->r.getString(1),rating,id);
        if(rows.isEmpty())throw BizException.notFound("图片不存在或评价已不可见");return bytes(rows.getFirst());
    }
    private byte[] bytes(String filename){
        if(!filename.matches("[a-f0-9-]{36}\\.jpg"))throw BizException.notFound("图片不存在");
        try{return Files.readAllBytes(dir.resolve(filename));}catch(IOException e){throw BizException.notFound("图片暂不可用");}
    }
    public static void rollbackFile(Path path){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(path);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(PersonalMediaService.class).warn("Could not clean an uncommitted image");}}
            });
    }
}
