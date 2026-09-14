package com.maimai.identity.service;

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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

/** Public profile images are generated JPEGs in their own directory, never user-supplied URLs. */
@Service
public class AvatarService {
    private static final String FILENAME = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg";
    private final JdbcTemplate db;
    private final SimpleRateLimiter limiter;
    private final Path directory;

    public AvatarService(JdbcTemplate db, SimpleRateLimiter limiter,
                         @Value("${MAIMAI_AVATAR_DIR:.local/avatars}") String directory) {
        this.db=db; this.limiter=limiter; this.directory=Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public AvatarView upload(MultipartFile file) {
        long user = SecurityUtils.currentUserId();
        var statuses = db.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE", String.class, user);
        if (statuses.isEmpty() || !"ACTIVE".equals(statuses.getFirst())) throw BizException.unauthorized("账号不可用，请重新登录");
        limiter.require("avatar:"+user,20,3600,"头像上传过于频繁，请稍后重试");
        String filename=UUID.randomUUID()+".jpg";
        Path stored=directory.resolve(filename);
        byte[] encoded;
        try {encoded=SafeImageEncoder.encode(file,"AVATAR");}
        catch(IOException invalidImage) {throw BizException.badRequest("AVATAR_IMAGE_FORMAT","图片无法读取，请选择有效的JPG或PNG图片");}
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new IOException("Avatar directory must not be a symbolic link");
            restrict(directory,true);
            Files.createFile(stored);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) try {Files.deleteIfExists(stored);}
                    catch(IOException ignored) {org.slf4j.LoggerFactory.getLogger(AvatarService.class).warn("Rolled-back avatar cleanup failed");}
                }
            });
            Files.write(stored,encoded,StandardOpenOption.WRITE);
            restrict(stored,false);
            db.update("INSERT INTO user_avatars(user_id,filename,byte_size) VALUES(?,?,?) ON DUPLICATE KEY UPDATE filename=?,byte_size=?,updated_at=CURRENT_TIMESTAMP(6)",
                    user,filename,encoded.length,filename,encoded.length);
            return new AvatarView(url(filename));
        } catch(IOException error) {
            throw new BizException("AVATAR_IMAGE_STORE","头像暂时无法保存，请稍后重试",org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Transactional(readOnly=true)
    public Path readable(String filename) {
        if (filename==null || !filename.matches(FILENAME)) throw BizException.notFound("头像不存在");
        Long count=db.queryForObject("SELECT COUNT(*) FROM user_avatars a JOIN users u ON u.id=a.user_id WHERE a.filename=? AND u.status='ACTIVE'",Long.class,filename);
        Path path=directory.resolve(filename).normalize();
        if (count==null || count==0 || !path.getParent().equals(directory) || Files.isSymbolicLink(directory)
                || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw BizException.notFound("头像不存在");
        return path;
    }

    static String url(String filename) {return "/api/v1/avatars/"+filename;}
    private static void restrict(Path path,boolean isDirectory) throws IOException {
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class)!=null)
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(isDirectory?"rwx------":"rw-------"));
    }
    public record AvatarView(String avatarUrl) {}
}
