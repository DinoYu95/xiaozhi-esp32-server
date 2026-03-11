package xiaozhi.modules.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "技能信息")
public class AgentSkillVO {
    private String id;
    private String name;
    private String description;
    private String instructions;
    private String version;
    private String tools;
    private String metadata;
    @Schema(description = "是否官方推荐：0否 1是")
    private Integer isOfficialRecommended;
    private Date createTime;
    private Date updateTime;
}
