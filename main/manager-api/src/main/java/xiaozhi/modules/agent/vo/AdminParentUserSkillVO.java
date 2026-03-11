package xiaozhi.modules.agent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 后台管理-家长端技能（含家长信息）
 */
@Data
@Schema(description = "后台管理-家长端技能")
public class AdminParentUserSkillVO {

    @Schema(description = "技能 id")
    private Long id;
    @Schema(description = "家长用户 id")
    private Long parentUserId;
    @Schema(description = "家长昵称")
    private String parentNickname;
    @Schema(description = "技能名称")
    private String name;
    @Schema(description = "技能描述")
    private String description;
    @Schema(description = "技能指令")
    private String instructions;
    @Schema(description = "版本")
    private String version;
    @Schema(description = "工具列表 JSON")
    private String tools;
    @Schema(description = "元数据 JSON")
    private String metadata;
    @Schema(description = "创建时间")
    private Date createTime;
    @Schema(description = "更新时间")
    private Date updateTime;
}
