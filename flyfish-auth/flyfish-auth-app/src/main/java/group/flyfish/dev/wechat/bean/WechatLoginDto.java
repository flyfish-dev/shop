package group.flyfish.dev.wechat.bean;

import group.flyfish.dev.common.utils.IdGenerators;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 微信登录实体
 *
 * @author wangyu
 */
@Data
@AllArgsConstructor(staticName = "of")
public class WechatLoginDto {

    private boolean simple;

    private String url;

    private String sceneId;

    public static String newId() {
        return IdGenerators.uuid32();
    }
}
