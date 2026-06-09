package group.flyfish.dev.shop.wechat.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WechatKfOnlineListResponse implements WechatApiResponse {

    @JsonProperty("kf_online_list")
    private List<KfOnline> kfOnlineList;

    private Integer errcode;

    private String errmsg;

    public boolean hasOnlineKefu() {
        return kfOnlineList != null && !kfOnlineList.isEmpty();
    }

    @Data
    public static class KfOnline {
        @JsonProperty("kf_account")
        private String kfAccount;

        private Integer status;

        @JsonProperty("accepted_case")
        private Integer acceptedCase;
    }
}
