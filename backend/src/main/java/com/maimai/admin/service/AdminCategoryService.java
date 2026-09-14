package com.maimai.admin.service;

import com.maimai.admin.dto.GovernanceDtos.*;
import com.maimai.catalog.service.CategoryAvailability;
import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.*;

@Service
@Transactional
public class AdminCategoryService {
    private final JdbcTemplate db;
    private final GovernanceGuard guard;
    private final AdminAuditService audit;
    private final CategoryAvailability availability;
    public AdminCategoryService(JdbcTemplate db,GovernanceGuard guard,AdminAuditService audit,CategoryAvailability availability) {this.db=db;this.guard=guard;this.audit=audit;this.availability=availability;}
    @Transactional(readOnly=true)
    public List<CategoryItem> list() {
        guard.requireRole("SUPER_ADMIN","OPERATOR");
        return db.query("SELECT id,parent_id,name,sort,status FROM categories ORDER BY sort,id",(rs,n)->new CategoryItem(rs.getLong(1),rs.getObject(2,Long.class),rs.getString(3),rs.getInt(4),rs.getString(5)));
    }
    public CategoryItem save(Long id,CategoryWrite request) {
        guard.categories();guard.requireRole("SUPER_ADMIN","OPERATOR");
        validate(request); var before=id==null?null:find(id);
        var seen=new HashSet<Long>(); if(id!=null) seen.add(id); Long parent=request.parentId();
        while(parent!=null) {
            if(!seen.add(parent)||seen.size()>64) throw BizException.badRequest("CATEGORY_CYCLE","分类层级不能成环或超过64层");
            parent=find(parent).parentId();
        }
        if("ACTIVE".equals(request.status())&&request.parentId()!=null) availability.requireActive(request.parentId());
        String name=request.name().trim();
        if(id==null) {
            var key=new GeneratedKeyHolder();
            db.update(c->{var p=c.prepareStatement("INSERT INTO categories(parent_id,name,sort,status) VALUES (?,?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setObject(1,request.parentId());p.setString(2,name);p.setInt(3,request.sort());p.setString(4,request.status());return p;},key);
            id=Objects.requireNonNull(key.getKey()).longValue();
        } else db.update("UPDATE categories SET parent_id=?,name=?,sort=?,status=? WHERE id=?",request.parentId(),name,request.sort(),request.status(),id);
        var after=find(id);audit.record(before==null?"CATEGORY_CREATE":"CATEGORY_UPDATE","CATEGORY",id,request.reason().trim(),before==null?null:before.toString(),after.toString());
        return after;
    }
    private CategoryItem find(long id) {
        var found=db.query("SELECT id,parent_id,name,sort,status FROM categories WHERE id=?",(rs,n)->new CategoryItem(rs.getLong(1),rs.getObject(2,Long.class),rs.getString(3),rs.getInt(4),rs.getString(5)),id);
        if(found.isEmpty()) throw BizException.notFound("分类不存在");return found.getFirst();
    }
    private void validate(CategoryWrite r) {
        if(r==null||r.name()==null||r.name().isBlank()||r.name().length()>50||r.sort()<0||r.status()==null||!Set.of("ACTIVE","DISABLED").contains(r.status())||r.reason()==null||r.reason().isBlank()||r.reason().length()>500)
            throw BizException.badRequest("CATEGORY_INPUT_INVALID","请填写有效分类名称、排序、状态及操作原因");
    }
}
