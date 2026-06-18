package group.flyfish.dev.shop.service.support.h5zhifu.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Map;

@Data
public class H5ZhiFuNotifyDto {

    @JsonProperty("app_id")
    private Long appId;

    @JsonProperty("trade_no")
    private String tradeNo;

    @JsonProperty("in_trade_no")
    private String inTradeNo;

    @JsonProperty("out_trade_no")
    private String outTradeNo;

    @JsonProperty("trade_type")
    private String tradeType;

    private String description;

    @JsonProperty("pay_type")
    private String payType;

    private Integer amount;

    private String attach;

    @JsonProperty("create_time")
    private String createTime;

    @JsonProperty("pay_time")
    private String payTime;

    @JsonProperty("notify_count")
    private Integer notifyCount;

    private String sign;

    @JsonIgnore
    private Map<String, ?> rawParams;

    /**
     * 兼容 JSON 和 form 两种回调载体。
     *
     * <p>验签必须使用平台原始字段名参与排序，例如 app_id、out_trade_no、pay_time。
     * 因此这里既把参数转换成 Java 属性供业务使用，也保留 rawParams 给签名工具直接处理，
     * 避免 Java 驼峰属性名和 JSON 下划线字段名互转时引入不可见差异。</p>
     */
    public static H5ZhiFuNotifyDto from(Map<String, ?> params) {
        H5ZhiFuNotifyDto dto = new H5ZhiFuNotifyDto();
        dto.setAppId(toLong(params.get("app_id")));
        dto.setTradeNo(toString(params.get("trade_no")));
        dto.setInTradeNo(toString(params.get("in_trade_no")));
        dto.setOutTradeNo(toString(params.get("out_trade_no")));
        dto.setTradeType(toString(params.get("trade_type")));
        dto.setDescription(toString(params.get("description")));
        dto.setPayType(toString(params.get("pay_type")));
        dto.setAmount(toInteger(params.get("amount")));
        dto.setAttach(toString(params.get("attach")));
        dto.setCreateTime(toString(params.get("create_time")));
        dto.setPayTime(toString(params.get("pay_time")));
        dto.setNotifyCount(toInteger(params.get("notify_count")));
        dto.setSign(toString(params.get("sign")));
        dto.setRawParams(params);
        return dto;
    }

    private static String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
