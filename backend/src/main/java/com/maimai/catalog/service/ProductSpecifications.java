package com.maimai.catalog.service;

import com.maimai.common.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service
public class ProductSpecifications {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    public ProductSpecifications(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Field(String key,String label,String hint) {}
    private static Field field(String key,String label,String hint){return new Field(key,label,hint);}
    public List<Field> fields(long category){
        var names=jdbc.query("WITH RECURSIVE path AS (SELECT id,parent_id,name FROM categories WHERE id=? UNION ALL SELECT c.id,c.parent_id,c.name FROM categories c JOIN path p ON c.id=p.parent_id) SELECT name FROM path",(r,n)->r.getString(1),category);
        if(names.isEmpty())throw BizException.badRequest("CATEGORY_INVALID","请选择有效分类");
        String path=String.join("/",names);
        var fields=new ArrayList<>(List.of(field("brand","品牌 / 型号","请按实物填写，不清楚可留空"),field("accessories","随附配件","例如原装充电器、说明书")));
        if(path.contains("手机")||path.contains("电脑")||path.contains("数码"))fields.addAll(List.of(field("capacity","容量 / 规格","例如 256GB；按实际型号填写"),field("battery","电池状态","健康度或续航体验；不确定请说明"),field("repair","维修与拆修记录","是否更换过部件、是否拆修")));
        else if(path.contains("家居")||path.contains("家具"))fields.addAll(List.of(field("dimensions","尺寸","长 × 宽 × 高，请注明单位"),field("disassembly","拆装情况","能否拆卸，是否提供工具"),field("transport","搬运条件","楼层、电梯及搬运协助情况")));
        else if(path.contains("服饰")||path.contains("鞋包"))fields.addAll(List.of(field("size","尺码","尺码及实际测量尺寸"),field("material","材质","以标签或实际情况为准"),field("care","清洁与使用情况","是否清洗、磨损位置")));
        else if(path.contains("图书")||path.contains("教材"))fields.addAll(List.of(field("edition","版本 / ISBN","版本、出版年份或ISBN"),field("annotations","笔记与缺页","是否有涂写、缺页或配套资料")));
        else fields.addAll(List.of(field("dimensions","尺寸 / 规格","请注明尺寸、容量及单位"),field("usage","使用情况","使用年限、使用频率和存放情况")));
        return fields;
    }
    public String encode(long category,Map<String,String> values){
        var allowed=fields(category).stream().map(Field::key).collect(java.util.stream.Collectors.toSet());
        var clean=new TreeMap<String,String>();
        if(values!=null)for(var entry:values.entrySet()){
            if(!allowed.contains(entry.getKey()))throw BizException.badRequest("SPECIFICATION_FIELD","分类已变化，请核对商品参数后保存");
            String value=entry.getValue()==null?"":entry.getValue().strip();
            if(value.length()>200||value.indexOf('\0')>=0)throw BizException.badRequest("SPECIFICATION_LENGTH","单项商品参数请控制在200字以内");
            if(!value.isEmpty())clean.put(entry.getKey(),value);
        }
        return JSON.writeValueAsString(clean);
    }
    public static Map<String,String> decode(String value){
        if(value==null||value.isBlank())return Map.of();
        return JSON.readValue(value,new tools.jackson.core.type.TypeReference<Map<String,String>>(){});
    }
}
