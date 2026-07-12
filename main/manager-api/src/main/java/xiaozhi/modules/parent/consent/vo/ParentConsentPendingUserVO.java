package xiaozhi.modules.parent.consent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "未签署当前版本家长")
public class ParentConsentPendingUserVO {

    private Long parentUserId;
    private String nickname;
    private Date createTime;
}
