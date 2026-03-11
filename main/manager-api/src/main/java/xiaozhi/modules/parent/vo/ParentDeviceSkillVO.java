package xiaozhi.modules.parent.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端-设备下的技能信息")
public class ParentDeviceSkillVO {
    @Schema(description = "技能唯一标识，用于去重：官方=ai_skill.id，家长=parent_user_skill.id（与 search 返回的 id 一致）")
    private Object skillId;
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
    @Schema(description = "技能来源：official 官方推荐，parent 家长自建")
    private String skillSource;
    @Schema(description = "该技能对应的说话人类型列表，如 owner_child、parent")
    private List<String> speakerTypes;
    @Schema(description = "创建时间")
    private Date createTime;
    @Schema(description = "更新时间")
    private Date updateTime;
}
