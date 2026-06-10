package group.flyfish.dev.shop.wechat.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class WechatUserInfoResponse implements WechatApiResponse {

    private Integer subscribe;

    private String openid;

    private String nickname;

    private String language;

    @JsonProperty("headimgurl")
    private String headimgurl;

    @JsonProperty("subscribe_time")
    private Long subscribeTime;

    private String unionid;

    private String remark;

    private Integer groupid;

    @JsonProperty("tagid_list")
    private List<Integer> tagidList;

    private List<String> privilege;

    @JsonProperty("subscribe_scene")
    private String subscribeScene;

    @JsonProperty("qr_scene")
    private Integer qrScene;

    @JsonProperty("qr_scene_str")
    private String qrSceneStr;

    private Integer errcode;

    private String errmsg;

    public Map<String, Object> toSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("subscribe", subscribe);
        snapshot.put("openid", openid);
        snapshot.put("nickname", StringUtils.trimToEmpty(nickname));
        snapshot.put("language", language);
        snapshot.put("headimgurl", headimgurl);
        snapshot.put("subscribe_time", subscribeTime);
        snapshot.put("unionid", unionid);
        snapshot.put("remark", remark);
        snapshot.put("groupid", groupid);
        snapshot.put("tagid_list", tagidList);
        snapshot.put("privilege", privilege);
        snapshot.put("subscribe_scene", subscribeScene);
        snapshot.put("qr_scene", qrScene);
        snapshot.put("qr_scene_str", qrSceneStr);
        return snapshot;
    }
}
