package com.maimai.messaging.service;

import com.maimai.common.BizException;
import com.maimai.messaging.dto.MessageDtos.UploadedImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

@Service
public class MessageImageService {
    private final JdbcTemplate db;
    private final MessageService messages;
    private final Path root;
    public MessageImageService(JdbcTemplate db, MessageService messages,
        @Value("${maimai.messaging-image-dir:${MAIMAI_MESSAGE_IMAGE_DIR:.local/private-message-images}}") String directory) {
        this.db=db; this.messages=messages; this.root=Path.of(directory).toAbsolutePath().normalize();
    }

    @Transactional
    public UploadedImage upload(long actor,long conversation,MultipartFile file) {
        if (file.isEmpty() || file.getSize()>5*1024*1024) throw BizException.badRequest("MESSAGE_IMAGE_SIZE","请选择5MB以内的JPG或PNG图片");
        List<String> status=db.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE",String.class,actor);
        if(status.isEmpty() || !"ACTIVE".equals(status.getFirst())) throw BizException.forbidden("账号当前不可上传图片");
        messages.requireCanSend(actor,conversation);
        if(db.queryForObject("SELECT COUNT(*) FROM message_attachments WHERE uploaded_by=? AND created_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 HOUR)",Long.class,actor)>=20)
            throw BizException.tooMany("图片上传过于频繁，请稍后重试");
        try {
            byte[] encoded=com.maimai.common.SafeImageEncoder.encode(file,"MESSAGE");
            UUID id=UUID.randomUUID();
            String name=id+".jpg";
            Files.createDirectories(root);
            restrictPermissions(root,true);
            Path stored=root.resolve(name);
            Files.write(stored,encoded,StandardOpenOption.CREATE_NEW);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) {
                        try {Files.deleteIfExists(stored);} catch(IOException cleanupFailure) {
                            org.slf4j.LoggerFactory.getLogger(MessageImageService.class).warn("Rolled-back private image could not be removed; check storage permissions");
                        }
                    }
                }
            });
            restrictPermissions(stored,false);
            db.update("INSERT INTO message_attachments(id,conversation_id,uploaded_by,storage_name,media_type,byte_size) VALUES(?,?,?,?,?,?)",
                id.toString(),conversation,actor,name,"image/jpeg",encoded.length);
            return new UploadedImage(id,MessageService.attachmentUrl(id.toString()),encoded.length);
        } catch(IOException ex) {
            throw BizException.badRequest("MESSAGE_IMAGE_READ","图片无法读取，请重新选择JPG或PNG图片");
        }
    }
    private static void restrictPermissions(Path path,boolean directory) throws IOException {
        if(Files.getFileAttributeView(path,PosixFileAttributeView.class)!=null)
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
    }

    @Transactional(readOnly=true)
    public Path readable(long actor,UUID id) {
        List<StoredImage> records=db.query("SELECT conversation_id,uploaded_by,storage_name FROM message_attachments WHERE id=?",
            (rs,n)->new StoredImage(rs.getLong(1),rs.getLong(2),rs.getString(3)),id.toString());
        if(records.isEmpty()) throw BizException.notFound("图片不存在或不可访问");
        StoredImage record=records.getFirst();
        messages.requireMember(actor,record.conversation());
        if(record.uploader()!=actor && db.queryForObject("SELECT COUNT(*) FROM direct_messages WHERE attachment_id=? AND conversation_id=?",Long.class,id.toString(),record.conversation())!=1)
            throw BizException.notFound("图片尚未发送");
        Path path=root.resolve(record.name()).normalize();
        if(!path.startsWith(root) || !Files.isRegularFile(path)) throw BizException.notFound("图片不存在");
        return path;
    }

    private record StoredImage(long conversation,long uploader,String name) {}
}
