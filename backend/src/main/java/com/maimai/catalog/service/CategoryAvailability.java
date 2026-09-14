package com.maimai.catalog.service;

import com.maimai.admin.service.GovernanceGuard;
import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;

/** Serialize category eligibility with administrative edits; ancestors must all remain active. */
@Service
public class CategoryAvailability {
    private final JdbcTemplate db;
    private final GovernanceGuard guard;
    public CategoryAvailability(JdbcTemplate db,GovernanceGuard guard) {this.db=db;this.guard=guard;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireActive(long categoryId) {
        guard.categories();
        var seen=new HashSet<Long>(); Long id=categoryId;
        while(id!=null) {
            if(!seen.add(id)||seen.size()>64) throw invalid();
            var rows=db.query("SELECT parent_id,status FROM categories WHERE id=?",(rs,n)->new Node(rs.getObject(1,Long.class),rs.getString(2)),id);
            if(rows.isEmpty()||!"ACTIVE".equals(rows.getFirst().status())) throw invalid();
            id=rows.getFirst().parent();
        }
    }
    private BizException invalid() {return BizException.badRequest("CATEGORY_INVALID","分类或其上级分类不可用，请重新选择");}
    private record Node(Long parent,String status) {}
}
