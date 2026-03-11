package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端自定义技能")
public class ParentUserSkillVO {

    @Schema(description = "技能 id")
    private Long id;
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
