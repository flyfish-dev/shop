package group.flyfish.dev.xianyu.trade;

import java.util.*;
import java.math.BigDecimal;

public final class ListingValidator {
    private ListingValidator() { }
    public static void validate(Map<String,Object> p) {
        String title=String.valueOf(p.getOrDefault("title",""));
        String description=String.valueOf(p.getOrDefault("desc",""));
        if (title.isBlank() || title.codePointCount(0,title.length())>30 || description.isBlank() || description.length()>5000)
            throw new IllegalArgumentException("商品标题或描述不符合平台限制");
        if (!String.valueOf(p.getOrDefault("channel_cat_id","")).matches("[0-9]+"))
            throw new IllegalArgumentException("请选择渠道类目");
        if (money(p.get("reserve_price"))<=0) throw new IllegalArgumentException("售价须大于零");
        if (!(p.get("images") instanceof List<?> images) || images.isEmpty() || images.size()>9
                || images.stream().anyMatch(id -> !String.valueOf(id).matches("[0-9]+")))
            throw new IllegalArgumentException("请使用商家所属的媒体文件编号");
        if (Integer.parseInt(String.valueOf(p.getOrDefault("quantity",0)))<=0)
            throw new IllegalArgumentException("请配置有效库存");
        if (p.get("item_sku_list") instanceof List<?> skus && !skus.isEmpty()) {
            Map<String,Set<String>> attributes=new LinkedHashMap<>();
            Set<String> codes=new HashSet<>();
            for (Object value:skus) {
                if (!(value instanceof Map<?,?> sku)) throw new IllegalArgumentException("SKU格式不正确");
                if (!codes.add(String.valueOf(sku.get("outer_id"))) || sku.get("outer_id")==null)
                    throw new IllegalArgumentException("SKU编码必须唯一");
                if (new BigDecimal(String.valueOf(sku.get("price"))).longValueExact()<=0
                        || Integer.parseInt(String.valueOf(sku.get("quantity")))<=0)
                    throw new IllegalArgumentException("SKU价格和库存须大于零");
                if (!(sku.get("property_list") instanceof List<?> props) || props.isEmpty() || props.size()>2)
                    throw new IllegalArgumentException("SKU最多支持两个销售属性");
                for (Object property:props) {
                    Map<?,?> pair=(Map<?,?>)property;
                    String name=String.valueOf(pair.get("property_text")), item=String.valueOf(pair.get("value_text"));
                    if (name.isBlank() || item.isBlank() || name.length()>30 || item.length()>30)
                        throw new IllegalArgumentException("销售属性不符合平台限制");
                    attributes.computeIfAbsent(name,k -> new HashSet<>()).add(item);
                }
            }
            if (attributes.size()>2 || attributes.values().stream().anyMatch(v -> v.size()<2 || v.size()>10))
                throw new IllegalArgumentException("每个销售属性须包含2至10个值");
        }
    }
    public static long money(Object yuan) {
        return new BigDecimal(String.valueOf(yuan)).movePointRight(2).longValueExact();
    }
}
