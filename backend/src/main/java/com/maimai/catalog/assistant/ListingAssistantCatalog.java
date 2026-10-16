package com.maimai.catalog.assistant;

import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Only categories whose entire ancestry remains active may be offered. */
@Component
public class ListingAssistantCatalog {
    private final JdbcTemplate db;
    public ListingAssistantCatalog(JdbcTemplate db){this.db=db;}
    record Node(long id,String name,Long parent){}
    Map<Long,Node> active(){
        var rows=db.query("SELECT id,name,parent_id FROM categories WHERE status='ACTIVE' ORDER BY sort,id",(r,n)->new Node(r.getLong(1),r.getString(2),r.getObject(3,Long.class)));
        Map<Long,Node> all=new LinkedHashMap<>();rows.forEach(r->all.put(r.id(),r));
        Map<Long,Node> valid=new LinkedHashMap<>();
        for(var row:rows)if(path(row.id(),all)!=null)valid.put(row.id(),row);
        return valid;
    }
    private String path(long id,Map<Long,Node> all){
        var names=new LinkedList<String>();var seen=new HashSet<Long>();Long current=id;
        while(current!=null){
            if(!seen.add(current)||seen.size()>32)return null;
            var node=all.get(current);if(node==null)return null;
            names.addFirst(node.name());current=node.parent();
        }
        return String.join(" / ",names);
    }
    public List<ListingVisionModel.CategoryOption> choices(){
        var all=active();var parents=new HashSet<Long>();all.values().forEach(n->{if(n.parent()!=null)parents.add(n.parent());});
        return all.values().stream().filter(n->!parents.contains(n.id())).map(n->new ListingVisionModel.CategoryOption(n.id(),path(n.id(),all))).limit(300).toList();
    }
    public Set<Long> descendants(long category){
        var all=active();if(!all.containsKey(category))throw BizException.badRequest("CATEGORY_INVALID","分类已调整，请重新选择大类和小分类");
        Set<Long> found=new LinkedHashSet<>();found.add(category);
        boolean changed;do{changed=false;for(var n:all.values())if(n.parent()!=null&&found.contains(n.parent()))changed|=found.add(n.id());}while(changed);
        return found;
    }
}
