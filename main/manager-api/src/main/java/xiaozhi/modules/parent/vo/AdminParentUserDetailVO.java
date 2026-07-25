package xiaozhi.modules.parent.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端-家长用户详情")
public class AdminParentUserDetailVO {

    private Long id;
    private String nickname;
    private String displayNickname;
    private String avatarUrl;
    private String phoneMasked;
    private Boolean betaTester;
    private Date createTime;
    private Date updateTime;
    private List<AdminParentUserAuthVO> auths;
    private List<AdminParentUserDeviceVO> devices;
}
