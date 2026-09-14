package com.maimai.community.service;

import com.maimai.common.BizException;
import com.maimai.community.dto.CommunityDtos.*;
import com.maimai.community.repository.CommunityRepository;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static com.maimai.community.service.CommunitySupport.*;

@Service
@Transactional(readOnly=true)
public class ProductDiscussionService {
    public record CreateComment(@NotBlank @Size(max=1000) String content, @Positive Long replyToId) {}
    public record Comment(long id,long productId,long authorId,String nickname,String avatarUrl,
                          boolean seller,String content,Long replyToId,String replyToNickname,String replyPreview,Instant createdAt) {}
    private final CommunityRepository repo;
    private final CommunitySupport support;
    public ProductDiscussionService(CommunityRepository repo,CommunitySupport support){this.repo=repo;this.support=support;}
    private static final String VISIBLE=" c.status='PUBLISHED' AND u.status='ACTIVE' ";
    private static final String SELECT="""
        SELECT c.*,u.nickname,CONCAT('/api/v1/avatars/',a.filename) avatar_url,p.seller_id,
          CASE WHEN parent.status='PUBLISHED' AND pu.status='ACTIVE' THEN pu.nickname END reply_nickname,
          CASE WHEN parent.status='PUBLISHED' AND pu.status='ACTIVE' THEN LEFT(parent.content,100) END reply_preview
        FROM product_comments c JOIN users u ON u.id=c.author_id JOIN products p ON p.id=c.product_id
        LEFT JOIN user_avatars a ON a.user_id=u.id
        LEFT JOIN product_comments parent ON parent.id=c.reply_to_id LEFT JOIN users pu ON pu.id=parent.author_id
        """;
    private static final org.springframework.jdbc.core.RowMapper<Comment> MAPPER=(rs,n)->new Comment(rs.getLong("id"),rs.getLong("product_id"),rs.getLong("author_id"),rs.getString("nickname"),rs.getString("avatar_url"),rs.getLong("seller_id")==rs.getLong("author_id"),rs.getString("content"),rs.getObject("reply_to_id",Long.class),rs.getString("reply_nickname"),rs.getString("reply_preview"),rs.getTimestamp("created_at").toInstant());
    public PageResult<Comment> list(long product,Integer page,Integer size){
        support.publicProduct(product);var p=paging(page,size);
        return p.result(repo.query(SELECT+" WHERE c.product_id=? AND "+VISIBLE+" ORDER BY c.created_at DESC,c.id DESC LIMIT ? OFFSET ?",MAPPER,product,p.size(),p.offset()),
            repo.count("SELECT COUNT(*) FROM product_comments c JOIN users u ON u.id=c.author_id WHERE c.product_id=? AND "+VISIBLE,product));
    }
    @Transactional
    public Comment create(long actor,long product,CreateComment request){
        support.lockUser(actor);support.publicProduct(product);
        if(repo.count("SELECT COUNT(*) FROM product_comments WHERE author_id=? AND created_at>DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE)",actor)>=5)throw BizException.tooMany("留言过于频繁，请稍后再试");
        if(request.replyToId()!=null && repo.count("SELECT COUNT(*) FROM product_comments c JOIN users u ON u.id=c.author_id WHERE c.id=? AND c.product_id=? AND "+VISIBLE,request.replyToId(),product)!=1)throw BizException.notFound("回复的留言不存在或不属于这件商品");
        long id=repo.insertReturningId("INSERT INTO product_comments(product_id,author_id,reply_to_id,content) VALUES(?,?,?,?)",product,actor,request.replyToId(),text(request.content(),"留言",1,1000));
        return repo.queryOne(SELECT+" WHERE c.id=?",MAPPER,id);
    }
    @Transactional
    public void delete(long actor,long product,long id){
        support.lockUser(actor);
        Long owner=repo.queryOne("SELECT author_id FROM product_comments WHERE id=? AND product_id=? FOR UPDATE",(rs,n)->rs.getLong(1),id,product);
        if(owner==null)throw BizException.notFound("留言不存在");
        if(owner!=actor)throw BizException.forbidden("只能删除自己的留言");
        repo.update("UPDATE product_comments SET status='DELETED',updated_at=UTC_TIMESTAMP(6) WHERE id=?",id);
    }
}
