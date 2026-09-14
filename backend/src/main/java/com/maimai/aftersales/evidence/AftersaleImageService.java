package com.maimai.aftersales.evidence;

import com.maimai.common.BizException;
import com.maimai.common.SafeImageEncoder;
import com.maimai.common.SimpleRateLimiter;
import com.maimai.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.*;

@Service
public class AftersaleImageService {
    private final JdbcTemplate jdbc;
    private final SimpleRateLimiter limiter;
    private final Path directory;
    public AftersaleImageService(JdbcTemplate jdbc, SimpleRateLimiter limiter,
                                 @Value("${MAIMAI_AFTERSALE_IMAGE_DIR:.local/private-aftersale-images}") String directory) {
        this.jdbc=jdbc;this.limiter=limiter;this.directory=Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public ImageView upload(long aftersale, MultipartFile file) {
        CaseRow record = requireCase(aftersale, true);
        long actor = SecurityUtils.currentUserId();
        if (record.buyer()!=actor && record.seller()!=actor) throw BizException.forbidden("仅交易双方可补充证据");
        if (Set.of("RESOLVED", "CLOSED").contains(record.status())) throw BizException.conflict("AFTERSALE_CLOSED", "售后已结束，不能追加证据");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM aftersale_images WHERE aftersale_id=?", Long.class, aftersale)>=12)
            throw BizException.conflict("AFTERSALE_IMAGE_LIMIT", "每笔售后最多保存12张证据图");
        limiter.require("aftersale-image:"+actor, 20, 3600, "图片上传过于频繁，请稍后重试");
        UUID id = UUID.randomUUID();
        Path stored = directory.resolve(id+".jpg");
        try {
            byte[] content = SafeImageEncoder.encode(file, "AFTERSALE");
            Files.createDirectories(directory); restrict(directory, true);
            Files.write(stored, content, StandardOpenOption.CREATE_NEW);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try {Files.deleteIfExists(stored);}
                    catch (IOException ignored) {org.slf4j.LoggerFactory.getLogger(AftersaleImageService.class).warn("Rolled-back evidence image cleanup failed");}
                }
            });
            restrict(stored, false);
            jdbc.update("INSERT INTO aftersale_images(id,aftersale_id,uploaded_by,storage_name,byte_size) VALUES(?,?,?,?,?)",
                    id.toString(), aftersale, actor, id+".jpg", content.length);
            return jdbc.queryForObject("SELECT id,uploaded_by,created_at FROM aftersale_images WHERE id=?",
                    (rs,n)->new ImageView(UUID.fromString(rs.getString(1)), url(rs.getString(1)), rs.getLong(2), rs.getTimestamp(3).toInstant()), id.toString());
        } catch (IOException error) {throw new BizException("AFTERSALE_IMAGE_STORE", "图片暂时无法保存，请稍后重试", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);}
    }

    @Transactional(readOnly=true)
    public List<ImageView> list(long aftersale) {
        requireCase(aftersale, false);
        return jdbc.query("SELECT id,uploaded_by,created_at FROM aftersale_images WHERE aftersale_id=? ORDER BY created_at,id LIMIT 12",
                (rs,n)->new ImageView(UUID.fromString(rs.getString(1)), url(rs.getString(1)), rs.getLong(2), rs.getTimestamp(3).toInstant()), aftersale);
    }

    @Transactional(readOnly=true)
    public Path readable(UUID id) {
        var images = jdbc.query("SELECT aftersale_id,storage_name FROM aftersale_images WHERE id=?",
                (rs,n)->new StoredImage(rs.getLong(1),rs.getString(2)), id.toString());
        if (images.isEmpty()) throw BizException.notFound("证据图片不存在或不可访问");
        requireCase(images.getFirst().aftersale(), false);
        Path path = directory.resolve(images.getFirst().filename()).normalize();
        if (!path.startsWith(directory) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw BizException.notFound("证据图片不存在");
        return path;
    }

    private CaseRow requireCase(long id, boolean lock) {
        // 与售后服务保持先锁售后行再读取订单的次序，不在此锁订单，避免交叉锁顺序。
        var cases = jdbc.query("SELECT id,order_id,status FROM aftersales WHERE id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new RawCase(rs.getLong(1),rs.getLong(2),rs.getString(3)), id);
        if (cases.isEmpty()) throw BizException.notFound("售后不存在或不可访问");
        var raw = cases.getFirst();
        var orders = jdbc.query("SELECT buyer_id,seller_id FROM orders WHERE id=?",
                (rs,n)->new CaseRow(rs.getLong(1),rs.getLong(2),raw.status()), raw.order());
        if (orders.isEmpty()) throw BizException.notFound("售后不存在或不可访问");
        var item = orders.getFirst(); var actor = SecurityUtils.current();
        if (actor.id()!=item.buyer() && actor.id()!=item.seller() && !actor.hasRole("SUPPORT") && !actor.hasRole("SUPER_ADMIN"))
            throw BizException.notFound("售后不存在或不可访问");
        return item;
    }

    private static void restrict(Path path, boolean directory) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class)!=null)
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
    }
    private static String url(String id) {return "/api/v1/aftersales/images/"+id;}
    private record CaseRow(long buyer,long seller,String status) {}
    private record RawCase(long id,long order,String status) {}
    private record StoredImage(long aftersale,String filename) {}
    public record ImageView(UUID id,String url,long uploadedBy,Instant createdAt) {}
}
