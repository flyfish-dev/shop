package group.flyfish.dev.shop.service.support.h5zhifu.bean;

import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

/**
 * h5支付返回值接取
 *
 * @param <T> 泛型
 */
@Data
public class H5ZhiFuResultDto<T> {

    @Property("状态码。200：成功调用，1001：签名错误，1002：系统错误，1003：缺少参数，1004：请求值格式错误")
    private Integer code;

    @Property("code字段所对应的详细描述信息")
    private String msg;

    @Property("实际的数据")
    private T data;

    public interface ResultCode {

        int SUCCESS = 200;

        int SIGN_ERROR = 1001;

        int SYSTEM_ERROR = 1002;

        int LACK_PARAM = 1003;

        int FORMAT_ERROR = 1004;
    }
}
