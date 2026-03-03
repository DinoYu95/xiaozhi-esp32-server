package xiaozhi.modules.agent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 智能体说话人类型→技能映射表
 */
@Data
@TableName("ai_agent_skill_mapping")
public class AgentSkillMappingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String agentId;
    /** 说话人类型: owner_child / parent / other_child / other_adult / unknown */
    @TableField("speaker_type")
    private String speakerType;
    @TableField("skill_id")
    private String skillId;
    private Date createTime;
    private Date updateTime;
}
