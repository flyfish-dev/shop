package group.flyfish.dev.user.domain.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 门户用户
 *
 * @author wangyu
 */
@Data
@Table("user_portal")
public class PortalUser {

    @Id
    private Long id;

    private String username;

    private String password;

    private String avatar;

    private String phone;

    private String email;

    private String bio;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updateTime;
}
