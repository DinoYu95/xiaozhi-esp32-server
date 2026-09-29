package xiaozhi.modules.parent.app.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测登录白名单项")
public class ParentBetaAllowlistVO {

    private Long id;
    private String wechatOpenid;
    private String channel;
    private String phone;
    private String source;
    private Boolean enabled;
    private Boolean reviewerTest;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
